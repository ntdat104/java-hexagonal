package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.service.DualCacheService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
public class DualCacheServiceImpl implements DualCacheService {

    /**
     * Local in-memory cache with TTL.
     */
    private final Map<String, CacheEntry> localCache = new ConcurrentHashMap<>();

    /**
     * Track ongoing refresh calls to avoid concurrent DB hits (single-flight).
     */
    private final Map<String, CompletableFuture<?>> refreshFutures = new ConcurrentHashMap<>();

    private final RedisTemplate<String, Object> redis;

    public DualCacheServiceImpl(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    /**
     * Simple value wrapper with TTL.
     */
    private static class CacheEntry {
        Object value;
        long expireAt;

        CacheEntry(Object value, Duration ttl) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttl.toMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    @Override
    public <T> T get(String cacheName, String key, Supplier<T> dbFetcher, Duration ttl) {
        String redisKey = cacheName + ":" + key;

        // 1️⃣ Check local cache
        CacheEntry entry = localCache.get(redisKey);
        if (entry != null) {
            if (!entry.isExpired()) {
                return (T) entry.value;
            } else {
                // ⚙️ Expired but still usable (refresh-ahead)
                T oldValue = (T) entry.value;
                triggerSingleFlightRefresh(cacheName, key, dbFetcher, ttl);
                return oldValue;
            }
        }

        // 2️⃣ Check Redis
        T value = (T) redis.opsForValue().get(redisKey);
        if (value != null) {
            localCache.put(redisKey, new CacheEntry(value, ttl));
            return value;
        }

        // 3️⃣ Miss => fetch from DB
        value = dbFetcher.get();
        if (value != null) {
            updateCachesAsync(redisKey, value, ttl);
        }

        return value;
    }

    /**
     * Avoids multiple concurrent refreshes for the same key.
     */
    private <T> void triggerSingleFlightRefresh(String cacheName, String key, Supplier<T> dbFetcher, Duration ttl) {
        String redisKey = cacheName + ":" + key;

        // Double-check: another thread might already be refreshing
        refreshFutures.computeIfAbsent(redisKey, k -> {
            CompletableFuture<Void> future = refresh(cacheName, key, dbFetcher, ttl)
                    .whenComplete((v, ex) -> refreshFutures.remove(k));
            return future;
        });
    }

    /**
     * Async background refresh.
     */
    @Async
    public <T> CompletableFuture<Void> refresh(String cacheName, String key, Supplier<T> dbFetcher, Duration ttl) {
        return CompletableFuture.runAsync(() -> {
            String redisKey = cacheName + ":" + key;
            T newValue = dbFetcher.get();
            if (newValue != null) {
                updateCaches(redisKey, newValue, ttl);
            }
        });
    }

    private <T> void updateCachesAsync(String redisKey, T value, Duration ttl) {
        CompletableFuture.runAsync(() -> updateCaches(redisKey, value, ttl));
    }

    private <T> void updateCaches(String redisKey, T value, Duration ttl) {
        localCache.put(redisKey, new CacheEntry(value, ttl));
        redis.opsForValue().set(redisKey, value, ttl);
    }

    /**
     * Evict one entry.
     */
    @Override
    public void evict(String cacheName, String key) {
        String redisKey = cacheName + ":" + key;
        localCache.remove(redisKey);
        redis.delete(redisKey);
    }

    /**
     * Evict all entries for cacheName.
     */
    @Override
    public void evict(String cacheName) {
        // Local
        Set<String> keys = localCache.keySet();
        keys.removeIf(k -> k.startsWith(cacheName + ":"));

        // Redis
        var redisKeys = redis.keys(cacheName + ":*");
        if (redisKeys != null && !redisKeys.isEmpty()) {
            redis.delete(redisKeys);
        }
    }
}
