package com.aieap.platform.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;

public class SafeStringMapValidator implements ConstraintValidator<SafeStringMap, Map<String, String>> {

    private int maxEntries;
    private int maxKeyLength;
    private int maxValueLength;

    @Override
    public void initialize(SafeStringMap annotation) {
        this.maxEntries = annotation.maxEntries();
        this.maxKeyLength = annotation.maxKeyLength();
        this.maxValueLength = annotation.maxValueLength();
    }

    @Override
    public boolean isValid(Map<String, String> value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (value.size() > maxEntries) {
            return false;
        }
        for (Map.Entry<String, String> entry : value.entrySet()) {
            if (!isSafe(entry.getKey(), maxKeyLength) || !isSafe(entry.getValue(), maxValueLength)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSafe(String value, int maxLength) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            return false;
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}