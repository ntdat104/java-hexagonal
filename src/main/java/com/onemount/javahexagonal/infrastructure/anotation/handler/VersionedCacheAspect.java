package com.onemount.javahexagonal.infrastructure.anotation.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onemount.javahexagonal.infrastructure.anotation.AutoVersionCache;
import com.onemount.javahexagonal.infrastructure.anotation.BumpVersion;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.StringJoiner;

@Aspect
@Component
public class VersionedCacheAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public VersionedCacheAspect(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(autoCache)")
    public Object handleRead(ProceedingJoinPoint joinPoint, AutoVersionCache autoCache) throws Throwable {
        Object userId = parseSpel(joinPoint, autoCache.key());
        String entity = autoCache.entity();

        // 1. Lấy version
        String vKey = "version:" + entity + ":" + userId;
        String version = redisTemplate.opsForValue().get(vKey) != null ?
                redisTemplate.opsForValue().get(vKey) : "0";

        // 2. Xử lý nhiều extraKeys
        String combinedExtraKey = "default";
        if (autoCache.extraKeys().length > 0) {
            StringJoiner joiner = new StringJoiner("_");
            for (String expression : autoCache.extraKeys()) {
                Object value = parseSpel(joinPoint, expression);
                joiner.add(value != null ? value.toString() : "null");
            }
            combinedExtraKey = joiner.toString();
        }

        // 3. Tạo final key: wallet:data:1001:0_10_id_desc:v1
        String finalCacheKey = String.format("%s:data:%s:%s:v%s",
                entity, userId, combinedExtraKey, version);

        // 4. Logic check cache và thực thi (như cũ)
        String cachedValue = redisTemplate.opsForValue().get(finalCacheKey);
        if (cachedValue != null) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return objectMapper.readValue(cachedValue, signature.getReturnType());
        }

        Object result = joinPoint.proceed();
        if (result != null) {
            redisTemplate.opsForValue().set(finalCacheKey, objectMapper.writeValueAsString(result), Duration.ofHours(1));
        }
        return result;
    }

    @AfterReturning("@annotation(bump)")
    public void handleWrite(JoinPoint joinPoint, BumpVersion bump) {
        Object userId = parseSpel(joinPoint, bump.key());
        redisTemplate.opsForValue().increment("version:" + bump.entity() + ":" + userId);
    }

    private Object parseSpel(JoinPoint joinPoint, String expression) {
        if (expression == null || expression.isEmpty()) return "default";

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        EvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(),
                signature.getMethod(),
                joinPoint.getArgs(),
                nameDiscoverer
        );
        return parser.parseExpression(expression).getValue(context);
    }
}