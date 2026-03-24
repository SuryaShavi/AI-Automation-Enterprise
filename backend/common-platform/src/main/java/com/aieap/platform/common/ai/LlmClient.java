package com.aieap.platform.common.ai;

import java.util.List;

public interface LlmClient {
    boolean isConfigured();

    default String completion(String systemPrompt, String userPrompt) {
        return completion(systemPrompt, userPrompt, CompletionOptions.defaults());
    }

    String completion(String systemPrompt, String userPrompt, CompletionOptions options);

    List<Double> embedding(String input);

    record CompletionOptions(int maxOutputTokens, double temperature) {
        public static CompletionOptions defaults() {
            return new CompletionOptions(700, 0.2);
        }
    }
}
