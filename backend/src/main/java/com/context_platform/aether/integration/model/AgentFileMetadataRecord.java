package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentFileMetadataRecord(
    UUID id,
    UUID projectId,
    UUID deviceId,
    String relativePath,
    String contentHash,
    long fileSizeBytes,
    Instant mtime,
    String language,
    List<String> symbols,
    Instant updatedAt
) {}

