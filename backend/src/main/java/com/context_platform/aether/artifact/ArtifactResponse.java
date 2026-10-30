package com.context_platform.aether.artifact;

import java.time.Instant;
import java.util.UUID;

public record ArtifactResponse(
    UUID id,
    UUID projectId,
    String title,
    String sourcePlatform,
    String sourceReference,
    String artifactType,
    UUID relatedTaskId,
    int currentVersion,
    String contentHash,
    long byteSize,
    String content, // null for list summary, present for get
    Instant createdAt
) {}

