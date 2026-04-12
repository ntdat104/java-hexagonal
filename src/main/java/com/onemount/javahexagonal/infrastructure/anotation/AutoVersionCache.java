package com.onemount.javahexagonal.infrastructure.anotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoVersionCache {
    String entity();
    String key(); // SpEL cho userId
    String[] extraKeys() default {}; // Mảng SpEL cho các tham số khác
}