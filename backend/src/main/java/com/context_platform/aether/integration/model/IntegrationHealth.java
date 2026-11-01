package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationHealth(
    UUID projectId,
    ProviderType provider,
    IntegrationStatus status,
    Instant lastSyncAt,
    Instant lastHealthCheckAt,
    String message,
    Map<String, Object> details
) {}

