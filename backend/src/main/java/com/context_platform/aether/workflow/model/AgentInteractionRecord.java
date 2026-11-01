package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentInteractionRecord(
        UUID id,
        UUID projectId,
        UUID sessionId,
        UUID taskId,
        String interactionType,
        String toolName,
        UUID artifactId,
        Map<String, Object> details,
        Instant createdAt
) {}

