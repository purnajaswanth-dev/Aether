package com.context_platform.aether.embedding.model;

import java.time.Instant;
import java.util.UUID;

public record ChunkEmbedding(
    UUID id,
    UUID projectId,
    UUID chunkId,
    String modelName,
    String modelVersion,
    int dimension,
    float[] embedding,
    Instant createdAt
) {}

