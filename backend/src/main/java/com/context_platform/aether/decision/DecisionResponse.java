package com.context_platform.aether.decision;

import java.time.Instant;
import java.util.UUID;

public record DecisionResponse(
    UUID id,
    UUID projectId,
    String publicId,
    String title,
    String state,
    String category,
    int currentVersion,
    int lockVersion,
    String problemStatement,
    String proposedSolution,
    String rationale,
    String consequences,
    String latestActorId,
    String latestActorType,
    Instant createdAt,
    Instant updatedAt
) {}

