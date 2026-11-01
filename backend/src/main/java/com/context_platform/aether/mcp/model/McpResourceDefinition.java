package com.context_platform.aether.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpResourceDefinition(
    String uri,
    String name,
    String description,
    String mimeType
) {}

