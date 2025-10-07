package com.onemount.javahexagonal.infrastructure.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        // ✅ Replace this logic with your authentication system (e.g., Spring Security)
        // For now, return a fixed user ID (e.g., admin = 1L)
        return Optional.of(1L);
    }
}
