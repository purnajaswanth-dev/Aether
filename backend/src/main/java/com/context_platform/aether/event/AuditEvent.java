package com.context_platform.aether.event;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
    UUID id,
    UUID projectId,
    UUID userId,
    String action,
    String actorType,
    String ipAddress,
    String userAgent,
    String details,
    Instant createdAt
) {}
