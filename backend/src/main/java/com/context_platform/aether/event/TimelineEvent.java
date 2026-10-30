package com.context_platform.aether.event;

import java.time.Instant;
import java.util.UUID;

public record TimelineEvent(
    UUID id,
    UUID projectId,
    UUID taskId,
    String sourcePlatform,
    Instant timestamp,
    String eventType,
    UUID associatedArtifactId,
    UUID associatedDecisionId,
    Integer decisionVersion,
    String status,
    String actorId,
    String actorType,
    String summary,
    String metadata,
    Instant createdAt
) {}

