package com.context_platform.aether.extraction.model;

import java.time.Instant;
import java.util.UUID;

public record ExtractedFact(
    UUID id,
    UUID projectId,
    UUID artifactId,
    int artifactVersion,
    FactCategory category,
    String factKey,
    String factStatement,
    String exactQuote,
    int startOffset,
    int endOffset,
    String sourcePlatform,
    String extractionMethod,
    ValidationState validationState,
    String validationNotes,
    String approvalState,
    double confidenceScore,
    UUID reviewedBy,
    Instant reviewedAt,
    Instant createdAt
) {}

