package com.onemount.javahexagonal.infrastructure.anotation.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onemount.javahexagonal.infrastructure.anotation.VersionCache;
import com.onemount.javahexagonal.infrastructure.anotation.BumpVersion;
import lombok.RequiredArgsConstructor;
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

import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Aspect
@Component
public class VersionCacheAspect {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final VersionLocalCache localCache;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    // Hằng số cho thời gian sống của version key (nên dài hơn TTL của data)
    private static final long VERSION_KEY_TTL_DAYS = 3;

    // ================= READ =================
    @Around("@annotation(autoCache)")
    public Object handleRead(ProceedingJoinPoint joinPoint, VersionCache autoCache) throws Throwable {

        Object userId = parseSpel(joinPoint, autoCache.userId());
        String entity = autoCache.entity();

        String vKey = buildVersionKey(entity, userId);

        // 🔥 1. Lấy version từ local cache
        String version = localCache.get(vKey);

        if (version == null) {
            // 🔥 2. fallback Redis
            version = redisTemplate.opsForValue().get(vKey);

            if (version == null) {
                version = "0";
            }

            // 🔥 3. lưu local
            localCache.put(vKey, version);
        }

        // 4. build extra key
        String extraKey = buildExtraKey(joinPoint, autoCache.extraKeys());

        // 5. final cache key
        String finalKey = buildDataKey(entity, userId, extraKey, version);

        // 6. check Redis cache
        String cachedValue = redisTemplate.opsForValue().get(finalKey);
        if (cachedValue != null) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return objectMapper.readValue(cachedValue, signature.getReturnType());
        }

        // 7. cache miss
        Object result = joinPoint.proceed();

        if (result != null) {
            redisTemplate.opsForValue().set(
                    finalKey,
                    objectMapper.writeValueAsString(result),
                    autoCache.ttl(),
                    autoCache.unit()
            );
        }

        return result;
    }

    // ================= WRITE =================
    @AfterReturning("@annotation(bump)")
    public void handleWrite(JoinPoint joinPoint, BumpVersion bump) {

        Object userId = parseSpel(joinPoint, bump.userId());
        String entity = bump.entity();

        String vKey = buildVersionKey(entity, userId);

        // 🔥 1. increment Redis
        Long newVersion = redisTemplate.opsForValue().increment(vKey);

        redisTemplate.expire(vKey, VERSION_KEY_TTL_DAYS, TimeUnit.DAYS);

        // 🔥 2. update local ngay (QUAN TRỌNG)
        if (newVersion != null) {
            localCache.put(vKey, String.valueOf(newVersion));
        } else {
            localCache.invalidate(vKey);
        }
    }

    // ================= HELPER =================

    private String buildVersionKey(String entity, Object userId) {
        return "version:" + entity + ":" + userId;
    }

    private String buildDataKey(String entity, Object userId, String extraKey, String version) {
        return String.format("%s:data:%s:%s:v%s", entity, userId, extraKey, version);
    }

    private String buildExtraKey(JoinPoint joinPoint, String[] expressions) {
        if (expressions == null || expressions.length == 0) {
            return "default";
        }

        StringJoiner joiner = new StringJoiner("_");

        for (String expr : expressions) {
            Object value = parseSpel(joinPoint, expr);
            joiner.add(value != null ? value.toString() : "null");
        }

        return joiner.toString();
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