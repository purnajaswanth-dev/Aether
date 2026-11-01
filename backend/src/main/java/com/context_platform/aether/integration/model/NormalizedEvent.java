package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.Map;

public record NormalizedEvent(
    String eventId,
    String eventType,
    ProviderType provider,
    Instant timestamp,
    String summary,
    String rawPayload,
    Map<String, Object> metadata,
    String idempotencyKey
) {}

