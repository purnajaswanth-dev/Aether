package com.context_platform.aether.artifact;

import java.time.Instant;
import java.util.UUID;

public record ArtifactVersionResponse(
    UUID id,
    UUID artifactId,
    int version,
    String contentHash,
    long byteSize,
    String createdBy,
    String actorType,
    String changeSummary,
    String content, // null for summary list
    Instant createdAt
) {}
