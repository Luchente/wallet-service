package com.example.walletservice.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String errorCode,
        String message,
        Instant timestamp,
        String path,
        Map<String, Object> details
) {}