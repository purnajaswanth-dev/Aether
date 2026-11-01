package com.context_platform.aether.mcp.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateMcpTokenResponse(
    UUID id,
    UUID projectId,
    String tokenName,
    McpClientPlatform clientPlatform,
    String rawToken, // displayed only once!
    String tokenPrefix,
    List<String> permissions,
    int rateLimitPerMinute,
    Instant expiresAt,
    Instant createdAt
) {}

