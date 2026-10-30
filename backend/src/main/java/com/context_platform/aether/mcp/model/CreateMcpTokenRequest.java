package com.context_platform.aether.mcp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public record CreateMcpTokenRequest(
    @NotBlank String tokenName,
    McpClientPlatform clientPlatform,
    @NotEmpty List<String> permissions,
    Integer rateLimitPerMinute,
    Instant expiresAt
) {}
