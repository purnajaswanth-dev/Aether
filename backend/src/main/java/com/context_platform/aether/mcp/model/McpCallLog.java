package com.context_platform.aether.mcp.model;

import java.time.Instant;
import java.util.UUID;

public record McpCallLog(
    UUID id,
    UUID projectId,
    UUID tokenId,
    String toolName,
    McpClientPlatform clientPlatform,
    long durationMs,
    String status, // SUCCESS, ERROR, RATE_LIMITED, UNAUTHORIZED
    String errorMessage,
    Instant createdAt
) {}
