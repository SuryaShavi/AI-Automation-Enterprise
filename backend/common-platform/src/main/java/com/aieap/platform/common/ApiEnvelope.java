package com.aieap.platform.common;

import java.time.Instant;

public record ApiEnvelope<T>(
    Instant timestamp,
    String traceId,
    T data,
    ApiError error
) {
}