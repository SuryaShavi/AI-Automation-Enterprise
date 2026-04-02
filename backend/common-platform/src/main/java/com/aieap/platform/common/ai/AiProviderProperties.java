package com.aieap.platform.common.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "ai.provider")
public class AiProviderProperties {
    private String apiKey;
    private String apiKeyRotation;
    private String model = "gpt-4o-mini";
    private String embeddingModel = "text-embedding-3-small";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;
    private int maxRetries = 2;
    private long retryBackoffMs = 750;
    private int maxPromptChars = 12000;
    private int maxPromptTokens = 3000;
    private int maxContextChars = 6000;
    private int maxContextTokens = 1600;
    private int maxOutputTokens = 700;
    private int plannerMaxOutputTokens = 180;
    private int maxHistoryMessages = 8;
    private int maxContextChunks = 6;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyRotation() {
        return apiKeyRotation;
    }

    public void setApiKeyRotation(String apiKeyRotation) {
        this.apiKeyRotation = apiKeyRotation;
    }

    public List<String> getApiKeyCandidates() {
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        addCandidates(uniqueKeys, apiKey);
        addCandidates(uniqueKeys, apiKeyRotation);
        addCandidates(uniqueKeys, System.getProperty("AI_PROVIDER_API_KEY"));
        addCandidates(uniqueKeys, System.getenv("AI_PROVIDER_API_KEY"));
        return new ArrayList<>(uniqueKeys);
    }

    private void addCandidates(LinkedHashSet<String> uniqueKeys, String rawCandidates) {
        if (!StringUtils.hasText(rawCandidates)) {
            return;
        }
        for (String candidate : rawCandidates.split(",")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                uniqueKeys.add(trimmed);
            }
        }
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public int getMaxPromptChars() {
        return maxPromptChars;
    }

    public void setMaxPromptChars(int maxPromptChars) {
        this.maxPromptChars = maxPromptChars;
    }

    public int getMaxPromptTokens() {
        return maxPromptTokens;
    }

    public void setMaxPromptTokens(int maxPromptTokens) {
        this.maxPromptTokens = maxPromptTokens;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getPlannerMaxOutputTokens() {
        return plannerMaxOutputTokens;
    }

    public void setPlannerMaxOutputTokens(int plannerMaxOutputTokens) {
        this.plannerMaxOutputTokens = plannerMaxOutputTokens;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public int getMaxContextChunks() {
        return maxContextChunks;
    }

    public void setMaxContextChunks(int maxContextChunks) {
        this.maxContextChunks = maxContextChunks;
    }
}
