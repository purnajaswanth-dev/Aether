package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CodeDependencyRecord(
        UUID id,
        UUID projectId,
        String sourcePath,
        String targetPath,
        String dependencyType,
        String confidence,
        Map<String, Object> metadata,
        Instant createdAt
) {}

