package com.context_platform.aether.decision;

import java.time.Instant;
import java.util.UUID;

public record DecisionVersion(
    UUID id,
    UUID decisionId,
    UUID projectId,
    int version,
    String state,
    String problemStatement,
    String proposedSolution,
    String rationale,
    String consequences,
    UUID sourceArtifactVersionId,
    String actorId,
    String actorType,
    String reviewNotes,
    Instant createdAt
) {}
