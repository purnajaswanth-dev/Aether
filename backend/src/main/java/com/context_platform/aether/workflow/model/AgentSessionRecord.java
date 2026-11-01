package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record AgentSessionRecord(
        UUID id,
        UUID projectId,
        String clientPlatform,
        UUID taskId,
        Instant startedAt,
        Instant endedAt,
        String status,
        String summary,
        Instant createdAt
) {}

