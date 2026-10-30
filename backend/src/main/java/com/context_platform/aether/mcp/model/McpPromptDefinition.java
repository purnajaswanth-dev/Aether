package com.context_platform.aether.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpPromptDefinition(
    String name,
    String description,
    List<PromptArgument> arguments
) {
    public record PromptArgument(
        String name,
        String description,
        boolean required
    ) {}
}
