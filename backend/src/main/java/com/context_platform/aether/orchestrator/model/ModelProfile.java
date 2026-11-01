package com.context_platform.aether.orchestrator.model;

public record ModelProfile(
    String modelId,
    int contextWindowTokens,
    int reservedResponseTokens,
    int reservedSystemTokens,
    int maxContextBudgetTokens
) {}

