package com.aieap.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;

public final class ResponseFactory {
    private ResponseFactory() {
    }

    public static <T> ApiEnvelope<T> success(HttpServletRequest request, T data) {
        return new ApiEnvelope<>(Instant.now(), traceId(request), data, null);
    }

    public static String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(CorrelationIdFilter.TRACE_ID_ATTRIBUTE);
        if (traceId != null) {
            return traceId.toString();
        }
        String header = request.getHeader(CorrelationIdFilter.TRACE_ID_HEADER);
        return header == null ? "n/a" : header;
    }
}