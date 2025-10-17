package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.dto.response.SystemTimeDto;
import com.onemount.javahexagonal.application.service.DualCacheService;
import com.onemount.javahexagonal.application.service.SystemService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
public class SystemServiceImpl implements SystemService {

    private final DualCacheService cacheService;

    public SystemServiceImpl(DualCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public SystemTimeDto getSystemTime() {
        String cacheName = "system";
        String key = "time";
        Duration ttl = Duration.ofMinutes(1);

        // 🧠 Using a lambda for dbFetcher (Supplier<SystemTimeDto>)
        return cacheService.get(cacheName, key, () -> {
            Date date = new Date();
            SystemTimeDto result = new SystemTimeDto();
            result.setTimestamp(date.getTime());
            result.setDatetime(date.toString());
            System.out.println(">>> Fetching new system time from 'DB' at " + date);
            return result;
        }, ttl);
    }
}
