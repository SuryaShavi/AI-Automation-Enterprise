package com.aieap.platform.common.ai;

import java.util.List;

public interface LlmClient {
    boolean isConfigured();

    String completion(String systemPrompt, String userPrompt);

    List<Double> embedding(String input);
}
