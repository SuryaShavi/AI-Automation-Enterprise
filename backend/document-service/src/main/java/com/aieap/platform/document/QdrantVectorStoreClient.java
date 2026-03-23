package com.aieap.platform.document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class QdrantVectorStoreClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final QdrantProperties properties;

    public QdrantVectorStoreClient(RestClient.Builder restClientBuilder, QdrantProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public boolean isAvailable() {
        try {
            Map<String, Object> response = restClient.get()
                .uri("/collections/" + properties.getCollection())
                .retrieve()
                .body(MAP_TYPE);
            return response != null;
        } catch (Exception ex) {
            return false;
        }
    }

    public void ensureCollection(int vectorSize) {
        int size = vectorSize > 0 ? vectorSize : properties.getVectorSize();
        Map<String, Object> payload = Map.of(
            "vectors", Map.of(
                "size", size,
                "distance", "Cosine"
            )
        );

        try {
            restClient.put()
                .uri("/collections/" + properties.getCollection())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to ensure Qdrant collection", ex);
        }
    }

    public void upsertPoint(String pointId, List<Double> vector, Map<String, Object> payload) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", pointId);
        point.put("vector", vector);
        point.put("payload", payload);

        Map<String, Object> body = Map.of("points", List.of(point));
        try {
            restClient.put()
                .uri("/collections/" + properties.getCollection() + "/points?wait=true")
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to upsert Qdrant point", ex);
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
                .body(MAP_TYPE);

            return extractPointIds(response);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to query Qdrant", ex);
        }
    }

    @SuppressWarnings("unchecked")
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
