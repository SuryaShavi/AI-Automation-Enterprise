package com.aieap.platform.common.ai;

import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final AiProviderProperties properties;

    public OpenAiCompatibleLlmClient(RestClient.Builder restClientBuilder, AiProviderProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public String completion(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("AI provider key is not configured");
        }

        Map<String, Object> payload = Map.of(
            "model", properties.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.2
        );

        try {
            Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .body(payload)
                .retrieve()
                .body(MAP_TYPE);

            return extractContent(response);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "LLM provider call failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object choicesRaw = response.get("choices");
        if (!(choicesRaw instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object first = choices.getFirst();
        if (!(first instanceof Map<?, ?> firstChoice)) {
            return "";
        }
        Object messageRaw = firstChoice.get("message");
        if (!(messageRaw instanceof Map<?, ?> message)) {
            return "";
        }
        Object content = message.get("content");
        return content == null ? "" : content.toString();
    }
}
