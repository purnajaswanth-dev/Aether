package com.context_platform.aether.mcp.model;

import java.util.Map;

public record McpReliabilityReportResponse(
    long totalCalls,
    long successfulCalls,
    long failedCalls,
    int sessionsTracked,
    double firstRetrievalBeforeSaveRate,
    double saveCompletionRate,
    Map<String, Long> topToolsCalled,
    Map<String, Long> platformDistribution
) {}

