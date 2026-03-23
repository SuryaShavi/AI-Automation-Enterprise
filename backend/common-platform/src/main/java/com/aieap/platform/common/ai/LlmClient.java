package com.aieap.platform.common.ai;

public interface LlmClient {
    boolean isConfigured();

    String completion(String systemPrompt, String userPrompt);
}
