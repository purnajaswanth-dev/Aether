package com.context_platform.aether.common;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
    String error,
    String message,
    int status,
    Instant timestamp,
    Map<String, String> validationErrors
) {
    public static ErrorResponse of(String error, String message, int status) {
        return new ErrorResponse(error, message, status, Instant.now(), null);
    }

    public static ErrorResponse withValidation(String error, String message, int status, Map<String, String> errors) {
        return new ErrorResponse(error, message, status, Instant.now(), errors);
    }
}
