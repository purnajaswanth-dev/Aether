package com.context_platform.aether.mcp.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record McpTokenResponse(
    UUID id,
    UUID projectId,
    String tokenName,
    McpClientPlatform clientPlatform,
    String tokenPrefix,
    List<String> permissions,
    boolean isRevoked,
    boolean isPaused,
    int rateLimitPerMinute,
    Instant expiresAt,
    Instant lastUsedAt,
    Instant createdAt
) {
    public static McpTokenResponse from(McpToken token) {
        return new McpTokenResponse(
            token.id(),
            token.projectId(),
            token.tokenName(),
            token.clientPlatform(),
            token.tokenPrefix(),
            token.permissions(),
            token.isRevoked(),
            token.isPaused(),
            token.rateLimitPerMinute(),
            token.expiresAt(),
            token.lastUsedAt(),
            token.createdAt()
        );
    }
}
