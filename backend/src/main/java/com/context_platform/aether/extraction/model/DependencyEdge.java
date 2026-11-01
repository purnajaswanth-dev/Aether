package com.context_platform.aether.extraction.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DependencyEdge(
    UUID id,
    UUID projectId,
    String sourceType,
    UUID sourceId,
    String sourceName,
    String targetType,
    UUID targetId,
    String targetName,
    String relationType,
    Map<String, Object> metadata,
    Instant createdAt
) {}

