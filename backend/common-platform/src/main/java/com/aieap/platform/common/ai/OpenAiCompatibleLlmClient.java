package com.aieap.platform.common.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final RestClient ollamaRestClient;
    private final AiProviderProperties properties;

    public OpenAiCompatibleLlmClient(RestClient.Builder restClientBuilder, AiProviderProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = buildRequestFactory(properties);
        this.restClient = restClientBuilder
            .requestFactory(requestFactory)
            .baseUrl(Objects.requireNonNull(properties.getBaseUrl(), "ai.provider.base-url must be configured"))
            .build();
        this.ollamaRestClient = restClientBuilder
            .requestFactory(buildRequestFactory(properties))
            .baseUrl(stripV1Suffix(properties.getBaseUrl()))
            .build();
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public String completion(String systemPrompt, String userPrompt, CompletionOptions options) {
        if (!isConfigured()) {
            throw new IllegalStateException("AI provider key is not configured");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", options == null ? 0.2 : options.temperature());

        int maxTokens = options == null || options.maxOutputTokens() <= 0
            ? properties.getMaxOutputTokens()
            : options.maxOutputTokens();
        if (maxTokens > 0) {
            payload.put("max_tokens", maxTokens);
        }

        try {
            Map<String, Object> response = withRetry(() -> restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .body(Objects.requireNonNull(payload))
                .retrieve()
                .body(Objects.requireNonNull(MAP_TYPE)));

            return extractContent(response);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "LLM provider call failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Double> embedding(String input) {
        if (!isConfigured()) {
            throw new IllegalStateException("AI provider key is not configured");
        }

        String embeddingModel = StringUtils.hasText(properties.getEmbeddingModel())
            ? properties.getEmbeddingModel()
            : properties.getModel();

        Map<String, Object> payload = Map.of(
            "model", embeddingModel,
            "input", input == null ? "" : input
        );

        try {
            Map<String, Object> response = withRetry(() -> restClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .body(Objects.requireNonNull(payload))
                .retrieve()
                .body(Objects.requireNonNull(MAP_TYPE)));

            return extractEmbedding(response);
        } catch (RestClientResponseException ex) {
            if (isLocalOllama() && ex.getStatusCode().value() == 404) {
                return embeddingWithOllamaFallback(input, embeddingModel);
            }
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Embedding provider call failed: " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Embedding provider call failed: " + ex.getMessage(), ex);
        }
    }

    private JdkClientHttpRequestFactory buildRequestFactory(AiProviderProperties config) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(1000, config.getConnectTimeoutMs())))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, config.getReadTimeoutMs())));
        return requestFactory;
    }

    private <T> T withRetry(Callable<T> callable) {
        RestClientException lastException = null;
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callable.call();
            } catch (RestClientException ex) {
                lastException = ex;
                if (attempt >= maxAttempts || !isRetryable(ex)) {
                    throw ex;
                }
                pauseBeforeRetry(attempt);
            } catch (Exception ex) {
                throw new IllegalStateException("Unexpected LLM client failure", ex);
            }
        }

        throw lastException == null ? new IllegalStateException("LLM call failed") : lastException;
    }

    private boolean isRetryable(RestClientException ex) {
        if (ex instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 429 || status == 502 || status == 503 || status == 504;
        }
        return true;
    }

    private void pauseBeforeRetry(int attempt) {
        long backoff = Math.max(100, properties.getRetryBackoffMs()) * attempt;
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", interruptedException);
        }
    }

    private List<Double> embeddingWithOllamaFallback(String input, String embeddingModel) {
        Map<String, Object> payload = Map.of(
            "model", embeddingModel,
            "input", input == null ? "" : input
        );

        Map<String, Object> response = withRetry(() -> ollamaRestClient.post()
            .uri("/api/embed")
            .body(payload)
            .retrieve()
            .body(Objects.requireNonNull(MAP_TYPE)));

        Object embeddingsRaw = response == null ? null : response.get("embeddings");
        if (embeddingsRaw instanceof List<?> embeddings && !embeddings.isEmpty()) {
            Object first = embeddings.getFirst();
            if (first instanceof List<?> values) {
                return values.stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::doubleValue)
                    .collect(Collectors.toList());
            }
        }

        return extractEmbedding(response);
    }

    private boolean isLocalOllama() {
        String baseUrl = properties.getBaseUrl();
        return baseUrl != null && baseUrl.contains("localhost:11434");
    }

    private String stripV1Suffix(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://localhost:11434";
        }
        return baseUrl.endsWith("/v1") ? baseUrl.substring(0, baseUrl.length() - 3) : baseUrl;
    }

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

    private List<Double> extractEmbedding(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }

        Object dataRaw = response.get("data");
        if (dataRaw instanceof List<?> data && !data.isEmpty()) {
            Object first = data.getFirst();
            if (first instanceof Map<?, ?> map) {
                Object embeddingRaw = map.get("embedding");
                if (embeddingRaw instanceof List<?> values) {
                    return values.stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .map(Number::doubleValue)
                        .collect(Collectors.toList());
                }
            }
        }

        Object direct = response.get("embedding");
        if (direct instanceof List<?> values) {
            return values.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
