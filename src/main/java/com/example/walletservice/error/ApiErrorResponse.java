package com.example.walletservice.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String path,
        Map<String, Object> details
) {
    public ApiErrorResponse {
        details = (details == null) ? Map.of() : Map.copyOf(details);
    }

    public static ApiErrorResponse of(String errorCode, String message, String path) {
        return new ApiErrorResponse(errorCode, message, Instant.now(), path, Map.of());
    }

    public static ApiErrorResponse of(String errorCode, String message, String path, Map<String, Object> details) {
        return new ApiErrorResponse(errorCode, message, Instant.now(), path, details);
    }
}