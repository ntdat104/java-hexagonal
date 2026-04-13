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
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    // Hằng số cho thời gian sống của version key (nên dài hơn TTL của data)
    private static final long VERSION_KEY_TTL_DAYS = 3;

    @Around("@annotation(autoCache)")
    public Object handleRead(ProceedingJoinPoint joinPoint, VersionCache autoCache) throws Throwable {
        Object userId = parseSpel(joinPoint, autoCache.userId());
        String entity = autoCache.entity();

        // 1. Lấy version hiện tại
        String vKey = "version:" + entity + ":" + userId;
        String version = redisTemplate.opsForValue().get(vKey);
        if (version == null) {
            version = "0";
            // Optional: Khởi tạo version nếu chưa có để đồng bộ TTL
            // redisTemplate.opsForValue().setIfAbsent(vKey, "0", VERSION_KEY_TTL_DAYS, TimeUnit.DAYS);
        }

        // 2. Xử lý các khóa phụ (Extra Keys)
        String combinedExtraKey = "default";
        if (autoCache.extraKeys().length > 0) {
            StringJoiner joiner = new StringJoiner("_");
            for (String expression : autoCache.extraKeys()) {
                Object value = parseSpel(joinPoint, expression);
                joiner.add(value != null ? value.toString() : "null");
            }
            combinedExtraKey = joiner.toString();
        }

        // 3. Tạo final key: [entity]:data:[userId]:[extra]:v[version]
        String finalCacheKey = String.format("%s:data:%s:%s:v%s",
                entity, userId, combinedExtraKey, version);

        // 4. Kiểm tra cache
        String cachedValue = redisTemplate.opsForValue().get(finalCacheKey);
        if (cachedValue != null) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return objectMapper.readValue(cachedValue, signature.getReturnType());
        }

        // 5. Cache miss: Thực thi method gốc
        Object result = joinPoint.proceed();

        if (result != null) {
            String jsonResult = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(
                    finalCacheKey,
                    jsonResult,
                    autoCache.ttl(),
                    autoCache.unit()
            );
        }
        return result;
    }

    @AfterReturning("@annotation(bump)")
    public void handleWrite(JoinPoint joinPoint, BumpVersion bump) {
        Object userId = parseSpel(joinPoint, bump.userId());
        String vKey = "version:" + bump.entity() + ":" + userId;

        // Tăng version lên 1 đơn vị
        redisTemplate.opsForValue().increment(vKey);

        // QUAN TRỌNG: Thiết lập hoặc gia hạn TTL cho version key
        // Điều này đảm bảo key version không tồn tại vĩnh viễn
        redisTemplate.expire(vKey, VERSION_KEY_TTL_DAYS, TimeUnit.DAYS);
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