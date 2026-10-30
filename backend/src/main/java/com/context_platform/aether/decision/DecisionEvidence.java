package com.context_platform.aether.decision;

import java.time.Instant;
import java.util.UUID;

public record DecisionEvidence(
    UUID id,
    UUID decisionId,
    UUID projectId,
    String evidenceType,
    String description,
    UUID artifactVersionId,
    String createdBy,
    Instant createdAt
) {}
