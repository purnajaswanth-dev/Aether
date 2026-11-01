package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record TaskLinkRecord(
        UUID id,
        UUID projectId,
        UUID taskId,
        TaskLinkType linkType,
        String targetId,
        String targetLabel,
        Instant createdAt
) {}

