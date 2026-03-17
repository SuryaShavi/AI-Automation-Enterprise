package com.aieap.platform.common;

import java.util.List;

public record ApiError(
    String code,
    String message,
    List<String> details
) {
}