package com.context_platform.aether.orchestrator.model;

public record OmittedItem(
    String itemId,
    String title,
    String reason,
    int estimatedTokens,
    String fetchCommand
) {}

