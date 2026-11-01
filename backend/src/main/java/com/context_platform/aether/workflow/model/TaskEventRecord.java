package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskEventRecord(
        UUID id,
        UUID projectId,
        UUID taskId,
        TaskEventType eventType,
        String oldStatus,
        String newStatus,
        ActorType actorType,
        String actorId,
        String reason,
        Map<String, Object> metadata,
        Instant createdAt
) {}

