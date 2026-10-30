package com.context_platform.aether.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpToolDefinition(
    String name,
    String description,
    Map<String, Object> inputSchema
) {}
