package com.context_platform.aether.orchestrator.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ContextPackageResponse(
    UUID packageId,
    UUID projectId,
    ContextRequestType requestType,
    String targetModel,
    int tokenBudget,
    int estimatedTokens,
    Map<String, Object> sections,
    List<OmittedItem> omittedItems,
    String formattedPackage,
    Instant createdAt
) {}

