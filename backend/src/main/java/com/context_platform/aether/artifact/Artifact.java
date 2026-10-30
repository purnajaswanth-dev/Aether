package com.context_platform.aether.artifact;

import java.time.Instant;
import java.util.UUID;

public record Artifact(
    UUID id,
    UUID projectId,
    String title,
    String sourcePlatform,
    String sourceReference,
    String artifactType,
    UUID relatedTaskId,
    Instant createdAt
) {}

