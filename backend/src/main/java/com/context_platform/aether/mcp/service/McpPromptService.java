package com.context_platform.aether.mcp.service;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.mcp.model.McpPromptDefinition;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.security.McpSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpPromptService {

    private final McpSecurityService securityService;

    public McpPromptService(McpSecurityService securityService) {
        this.securityService = securityService;
    }

    public List<McpPromptDefinition> listPrompts(McpToken token) {
        securityService.enforcePermission(token, "READ_PROJECT_CONTEXT");
        return List.of(
            new McpPromptDefinition(
                "review_architecture",
                "Guides an engineering agent through a formal architectural review of the project.",
                List.of(
                    new McpPromptDefinition.PromptArgument("focus_area", "Specific area to inspect, e.g. security, scalability, data integrity", false)
                )
            ),
            new McpPromptDefinition(
                "start_implementation",
                "Sets up an engineering agent to implement code in compliance with project memory and constitutional rules.",
                List.of(
                    new McpPromptDefinition.PromptArgument("feature_description", "Brief description of the feature to implement", true)
                )
            )
        );
    }

    public Map<String, Object> getPrompt(String name, Map<String, Object> arguments, McpToken token) {
        securityService.enforcePermission(token, "READ_PROJECT_CONTEXT");

        if ("review_architecture".equalsIgnoreCase(name)) {
            String focus = arguments != null && arguments.get("focus_area") != null ? arguments.get("focus_area").toString() : "general architecture & security";
            String promptText = """
                You are conducting a peer architecture review for this project.
                1. Call the `get_architecture` tool to retrieve the current architecture.
                2. Inspect the design for compliance, especially in %s.
                3. If you find architectural risks or improvements, call `record_review_finding` with specific severity and remediation advice.
                4. Remember: Do not attempt to overwrite the original architecture.
            """.formatted(focus);

            return Map.of(
                "description", "Architectural Review Prompt",
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", Map.of("type", "text", "text", promptText)
                    )
                )
            );
        } else if ("start_implementation".equalsIgnoreCase(name)) {
            String feature = arguments != null && arguments.get("feature_description") != null ? arguments.get("feature_description").toString() : "assigned feature";
            String promptText = """
                You are beginning implementation for: %s.
                Follow the constitutional engineering workflow:
                1. Call `get_project_overview`, `get_architecture`, and `get_decisions` to load project context.
                2. Verify existing patterns and conventions.
                3. Implement the feature in small, reviewable steps.
                4. If significant technical decisions are made, call `record_decision` to propose an ADR.
                5. Before finishing, call `save_project_artifact` or `save_conversation_output` to persist evidence.
            """.formatted(feature);

            return Map.of(
                "description", "Implementation Kickoff Prompt",
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", Map.of("type", "text", "text", promptText)
                    )
                )
            );
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "Unknown prompt: " + name);
    }
}
