package com.aieap.platform.common.ai;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {
    // Approximate token estimate for OpenAI-compatible BPE tokenizers.
    private static final double CHARS_PER_TOKEN = 4.0;

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int normalizedLength = text.replaceAll("\\s+", " ").trim().length();
        return Math.max(1, (int) Math.ceil(normalizedLength / CHARS_PER_TOKEN));
    }

    public String truncateToTokens(String text, int tokenLimit) {
        if (text == null || text.isBlank() || tokenLimit <= 0) {
            return "";
        }

        int charBudget = Math.max(1, (int) Math.floor(tokenLimit * CHARS_PER_TOKEN));
        if (text.length() <= charBudget) {
            return text;
        }

        return text.substring(0, charBudget);
    }
}
