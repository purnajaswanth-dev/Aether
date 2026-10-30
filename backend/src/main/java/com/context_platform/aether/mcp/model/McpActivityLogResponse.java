package com.context_platform.aether.mcp.model;

import java.time.Instant;
import java.util.UUID;

public record McpActivityLogResponse(
    UUID id,
    UUID projectId,
    UUID tokenId,
    String toolName,
    McpClientPlatform clientPlatform,
    long durationMs,
    String status,
    String errorMessage,
    Instant createdAt
) {
    public static McpActivityLogResponse from(McpCallLog log) {
        return new McpActivityLogResponse(
            log.id(),
            log.projectId(),
            log.tokenId(),
            log.toolName(),
            log.clientPlatform(),
            log.durationMs(),
            log.status(),
            log.errorMessage(),
            log.createdAt()
        );
    }
}
