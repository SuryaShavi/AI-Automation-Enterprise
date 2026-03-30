package com.aieap.platform.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InputSanitizer {

    private InputSanitizer() {
    }

    public static String nullableText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    public static String requiredText(String value) {
        String cleaned = nullableText(value);
        return cleaned == null ? "" : cleaned;
    }

    public static String fileName(String value) {
        String cleaned = requiredText(value)
            .replace('\\', '_')
            .replace('/', '_')
            .replace(':', '_')
            .replace('*', '_')
            .replace('?', '_')
            .replace('"', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('|', '_');
        return cleaned.isEmpty() ? "unnamed" : cleaned;
    }

    public static Map<String, String> stringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = requiredText(entry.getKey());
            String value = requiredText(entry.getValue());
            if (!key.isEmpty() && !value.isEmpty()) {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }
}
