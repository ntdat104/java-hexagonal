package com.onemount.javahexagonal.infrastructure.anotation.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class VersionLocalCache {

    private final Cache<String, String> cache;

    public VersionLocalCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(30 + ThreadLocalRandom.current().nextInt(10), TimeUnit.SECONDS) // 🔥 jitter
                .maximumSize(50_000)
                .build();
    }

    public String get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, String value) {
        cache.put(key, value);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }
}