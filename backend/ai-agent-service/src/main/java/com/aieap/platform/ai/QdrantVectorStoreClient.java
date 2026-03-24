package com.aieap.platform.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class QdrantVectorStoreClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final QdrantProperties properties;

    public QdrantVectorStoreClient(RestClient.Builder restClientBuilder, QdrantProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl(Objects.requireNonNull(properties.getBaseUrl(), "qdrant.base-url must be configured"))
            .build();
    }

    public boolean isAvailable() {
        try {
            Map<String, Object> response = restClient.get()
                .uri("/collections/" + properties.getCollection())
                .retrieve()
                .body(Objects.requireNonNull(MAP_TYPE));
            return response != null;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<String> searchPointIds(List<Double> vector, String documentId, int limit) {
        Map<String, Object> body = Map.of(
            "vector", vector,
            "limit", limit,
            "with_payload", false,
            "filter", Map.of(
                "must", List.of(
                    Map.of(
                        "key", "documentId",
                        "match", Map.of("value", documentId)
                    )
                )
            )
        );

        try {
            Map<String, Object> response = restClient.post()
                .uri("/collections/" + properties.getCollection() + "/points/search")
                .body(body)
                .retrieve()
                .body(Objects.requireNonNull(MAP_TYPE));
            return extractPointIds(response);
        } catch (RestClientException ex) {
            return List.of();
        }
    }

    private List<String> extractPointIds(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }

        Object resultRaw = response.get("result");
        if (!(resultRaw instanceof List<?> result)) {
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        for (Object item : result) {
            if (item instanceof Map<?, ?> map) {
                Object id = map.get("id");
                if (id != null) {
                    ids.add(id.toString());
                }
            }
        }
        return ids;
    }
}