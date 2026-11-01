package com.context_platform.aether.integration.model;

import java.util.List;

public record CapabilityDescriptor(
    ProviderType provider,
    String displayName,
    String description,
    SyncMethod primarySyncMethod,
    boolean supportsWebhooks,
    boolean supportsPolling,
    boolean supportsLogs,
    boolean supportsDeployments,
    boolean supportsArtifactSync,
    String authModel,
    List<String> eventTypes,
    String rateLimits,
    String limitations
) {}

