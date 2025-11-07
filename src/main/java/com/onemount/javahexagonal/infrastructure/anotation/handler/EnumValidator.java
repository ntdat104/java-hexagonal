package com.onemount.javahexagonal.infrastructure.anotation.handler;

import com.onemount.javahexagonal.infrastructure.anotation.EnumValid;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumValidator implements ConstraintValidator<EnumValid, String> {
    private Set<String> allowedValues;

    @Override
    public void initialize(EnumValid constraintAnnotation) {
        allowedValues = Arrays.stream(constraintAnnotation.enumClass().getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // Use @NotNull if needed
        return allowedValues.contains(value);
    }
}
