package com.onemount.javahexagonal.infrastructure.anotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionCache {
    String entity();         // Tên thực thể (e.g., "order", "profile")
    String userId();         // SpEL expression để lấy ID người dùng
    String[] extraKeys() default {}; // Các tham số phụ (e.g., "page", "size")
    long ttl() default 1;    // Thời gian sống của cache
    TimeUnit unit() default TimeUnit.MINUTES;
}