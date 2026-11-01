package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeadLetterEventRecord(
    UUID id,
    UUID projectId,
    ProviderType provider,
    String eventType,
    String payload,
    Map<String, Object> headers,
    String errorMessage,
    int attempts,
    String status,
    Instant createdAt,
    Instant lastRetryAt
) {}

