package com.onemount.javahexagonal.infrastructure.anotation.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.BufferedReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Aspect
@Component
public class LogsActivityHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(LogsActivityHandler.class);

    public static final String REQUEST_ID = "request_id";
    public static final String SERVICE_NAME = "service_name";
    public static final String SERVICE_VERSION = "service_version";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String LATENCY = "latency";
    public static final String METHOD = "method";
    public static final String PATH = "path";
    public static final String REQUEST_PARAMS = "request_params";
    public static final String X_API_KEY = "x_api_key";
    public static final String X_API_SECRET = "x_api_secret";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String SIGNATURE = "signature";
    public static final String STATUS = "status";
    public static final String CLIENT_IP = "client_ip";
    public static final String USER_AGENT = "user_agent";
    public static final String CODE = "code";
    public static final String MESSAGE = "message";
    public static final String TIMESTAMP = "timestamp";
    public static final String DATETIME = "datetime";
    public static final String REQUEST_BODY = "request_body";
    public static final String RESPONSE_BODY = "response_body";
    public static final String ERRORS = "errors";
    public static final String HEADERS = "headers";

    @SneakyThrows
    @Around("@annotation(com.onemount.javahexagonal.infrastructure.anotation.LogsActivity)")
    public Object logsActivityAnnotation(ProceedingJoinPoint joinPoint) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> logData = new HashMap<>();

        // Lấy thông tin HTTP
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = null;
        HttpServletResponse response = null;

        if (requestAttributes instanceof ServletRequestAttributes servletAttrs) {
            request = servletAttrs.getRequest();
            response = servletAttrs.getResponse();
        }

        String requestId = UUID.randomUUID().toString();
        logData.put(REQUEST_ID, requestId);
        logData.put(SERVICE_NAME, "java hexagonal");
        logData.put(SERVICE_VERSION, "1.0.0");
        logData.put(METHOD, request != null ? request.getMethod() : joinPoint.getSignature().getName());
        logData.put(PATH, request != null ? request.getRequestURI() : joinPoint.getSignature().toShortString());
        logData.put(START_TIME, startTime);
        logData.put(DATETIME, new Date().toString());
        logData.put(CLIENT_IP, request != null ? request.getRemoteAddr() : "N/A");
        logData.put(USER_AGENT, request != null ? request.getHeader("User-Agent") : "N/A");

        // Headers
        if (request != null) {
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name, request.getHeader(name));
            }
            logData.put(HEADERS, headers);
        }

        // Request Body
        if (request != null) {
            try {
                StringBuilder body = new StringBuilder();
                BufferedReader reader = request.getReader();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                logData.put(REQUEST_BODY, body.toString());
            } catch (Exception e) {
                logData.put(REQUEST_BODY, "Cannot read request body");
            }
        }

        // Request Params
        if (request != null) {
            logData.put(REQUEST_PARAMS, request.getParameterMap());
        } else {
            logData.put(REQUEST_PARAMS, joinPoint.getArgs());
        }

        Object result;
        try {
            // Thực thi method chính
            result = joinPoint.proceed();

            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;
            logData.put(END_TIME, endTime);
            logData.put(LATENCY, latency);
            logData.put(TIMESTAMP, Instant.now().toEpochMilli());
            logData.put(RESPONSE_BODY, result);
            logData.put(STATUS, response != null ? response.getStatus() : 200);
            logData.put(MESSAGE, "Success");

            logger.info("API LOG SUCCESS: {}", objectMapper.writeValueAsString(logData));
            return result;
        } catch (Throwable ex) {
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;

            logData.put(END_TIME, endTime);
            logData.put(LATENCY, latency);
            logData.put(ERRORS, ex.getMessage());
            logData.put(STATUS, 500);
            logData.put(MESSAGE, "Internal Server Error");

            logger.error("API LOG ERROR: {}", objectMapper.writeValueAsString(logData), ex);
            throw ex;
        }
    }

}
