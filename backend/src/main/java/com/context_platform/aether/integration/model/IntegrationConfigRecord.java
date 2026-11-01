package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationConfigRecord(
    UUID id,
    UUID projectId,
    ProviderType provider,
    IntegrationStatus status,
    Map<String, Object> configMetadata,
    Instant lastSyncAt,
    Instant createdAt,
    Instant updatedAt
) {}

