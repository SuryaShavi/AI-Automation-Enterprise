package com.aieap.platform.common;

import java.util.List;

public record PageEnvelope<T>(
    List<T> items,
    int page,
    int size,
    long total,
    String sort
) {
}