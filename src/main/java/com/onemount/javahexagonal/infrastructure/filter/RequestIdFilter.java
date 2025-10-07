package com.onemount.javahexagonal.infrastructure.filter;

import com.onemount.javahexagonal.application.constant.RequestKeyConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();

        // 1. Set for Logging/Tracing (MDC)
        MDC.put(RequestKeyConstant.X_REQUEST_ID, requestId);

        // 2. Set for Web Context Access (Attribute)
        request.setAttribute(RequestKeyConstant.X_REQUEST_ID, requestId);

        // Optionally, return in response header
        response.setHeader(RequestKeyConstant.X_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRUCIAL: Clean up MDC
            MDC.remove(RequestKeyConstant.X_REQUEST_ID);
        }
    }
}
