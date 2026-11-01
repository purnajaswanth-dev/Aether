package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record CodeSymbolRecord(
        UUID id,
        UUID projectId,
        String filePath,
        String language,
        String symbolName,
        String symbolType,
        String signature,
        int startLine,
        int endLine,
        Instant createdAt
) {}

