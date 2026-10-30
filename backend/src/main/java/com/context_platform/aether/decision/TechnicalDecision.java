package com.context_platform.aether.decision;

import java.time.Instant;
import java.util.UUID;

public record TechnicalDecision(
    UUID id,
    UUID projectId,
    String publicId,
    String title,
    String state,
    String category,
    int currentVersion,
    int lockVersion,
    Instant createdAt,
    Instant updatedAt
) {}
