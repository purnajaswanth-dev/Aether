package com.context_platform.aether.ingestion.model;

import java.time.Instant;
import java.util.UUID;

public record IngestionJob(
    UUID id,
    UUID projectId,
    UUID artifactId,
    int artifactVersion,
    IngestionStatus status,
    int attempts,
    int maxAttempts,
    String lastError,
    String stepDetails,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt
) {}

