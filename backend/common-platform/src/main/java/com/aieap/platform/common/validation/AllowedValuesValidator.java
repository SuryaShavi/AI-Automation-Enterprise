package com.aieap.platform.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AllowedValuesValidator implements ConstraintValidator<AllowedValues, String> {

    private Set<String> allowed;
    private boolean ignoreCase;
    private boolean allowNull;

    @Override
    public void initialize(AllowedValues constraintAnnotation) {
        this.ignoreCase = constraintAnnotation.ignoreCase();
        this.allowNull = constraintAnnotation.allowNull();
        this.allowed = new HashSet<>();
        Arrays.stream(constraintAnnotation.values())
            .map(this::normalize)
            .forEach(allowed::add);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return allowNull;
        }
        String normalized = normalize(value.trim());
        if (normalized.isEmpty()) {
            return false;
        }
        return allowed.contains(normalized);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return ignoreCase ? value.toUpperCase(Locale.ROOT) : value;
    }
}
