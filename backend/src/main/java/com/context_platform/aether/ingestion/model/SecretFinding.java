package com.context_platform.aether.ingestion.model;

import java.time.Instant;
import java.util.UUID;

public record SecretFinding(
    UUID id,
    UUID projectId,
    UUID artifactId,
    int artifactVersion,
    SecretFindingType findingType,
    String redactedSnippet,
    int lineNumber,
    int startOffset,
    int endOffset,
    double entropy,
    Instant createdAt
) {}

