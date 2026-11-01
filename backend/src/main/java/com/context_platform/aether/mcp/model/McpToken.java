package com.context_platform.aether.mcp.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record McpToken(
    UUID id,
    UUID projectId,
    McpClientPlatform clientPlatform,
    String tokenName,
    String tokenPrefix,
    String tokenHash,
    List<String> permissions,
    boolean isRevoked,
    boolean isPaused,
    int rateLimitPerMinute,
    Instant expiresAt,
    Instant lastUsedAt,
    Instant createdAt
) {
    public boolean isValid() {
        if (isRevoked || isPaused) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    public boolean hasPermission(String requiredPermission) {
        return permissions != null && permissions.contains(requiredPermission);
    }
}

