package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IntegrationSummaryResponse(
    UUID id,
    UUID projectId,
    ProviderType provider,
    String displayName,
    IntegrationStatus status,
    SyncMethod primarySyncMethod,
    Map<String, Object> configMetadata,
    String maskedCredential,
    Instant lastSyncAt,
    Instant createdAt
) {}

