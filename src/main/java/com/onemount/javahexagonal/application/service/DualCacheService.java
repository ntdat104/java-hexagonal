package com.onemount.javahexagonal.application.service;

import java.time.Duration;
import java.util.function.Supplier;

public interface DualCacheService {
    /**
     * Get data from cache (Caffeine + Redis).
     * If not present, fetch from DB using dbFetcher and store it in both caches.
     *
     * @param cacheName the name of the cache
     * @param key the cache key
     * @param dbFetcher a supplier to fetch data from DB
     * @param ttl time-to-live for the cache
     * @param <T> type of data
     * @return cached or freshly fetched data
     */
    <T> T get(String cacheName, String key, Supplier<T> dbFetcher, Duration ttl);

    /**
     * Evict a single entry from both caches by cache name and key.
     *
     * @param cacheName the cache name
     * @param key the key to evict
     */
    void evict(String cacheName, String key);

    /**
     * Evict all entries under a specific cache name from both caches.
     *
     * @param cacheName the cache name
     */
    void evict(String cacheName);
}
