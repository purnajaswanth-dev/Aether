package com.context_platform.aether.artifact;

import java.time.Instant;
import java.util.UUID;

public record ArtifactVersion(
    UUID id,
    UUID artifactId,
    UUID projectId,
    int version,
    String content,
    String storageKey,
    String contentHash,
    long byteSize,
    String createdBy,
    String actorType,
    String changeSummary,
    Instant createdAt
) {}
