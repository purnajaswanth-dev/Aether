package com.context_platform.aether.ingestion.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ArtifactChunk(
    UUID id,
    UUID projectId,
    UUID artifactId,
    int artifactVersion,
    int chunkIndex,
    String parentSectionPath,
    ChunkType chunkType,
    String content,
    String contentHash,
    int startOffset,
    int endOffset,
    int startLine,
    int endLine,
    Map<String, Object> metadata,
    Instant createdAt
) {}

