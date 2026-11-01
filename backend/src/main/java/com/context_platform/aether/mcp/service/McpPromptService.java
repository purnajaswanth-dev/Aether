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
                "Sets up an engineering agent to implement code in compliance with project memory, task rules, and verification evidence.",
                List.of(
                    new McpPromptDefinition.PromptArgument("feature_description", "Brief description of the feature to implement", true),
                    new McpPromptDefinition.PromptArgument("task_id", "Optional UUID of the task being implemented", false)
                )
            ),
            new McpPromptDefinition(
                "coordinate_task",
                "Guides an engineering agent through task context retrieval, change impact analysis, implementation tracking, and test verification.",
                List.of(
                    new McpPromptDefinition.PromptArgument("task_id", "UUID of the task to work on", true)
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
            String taskId = arguments != null && arguments.get("task_id") != null ? arguments.get("task_id").toString() : null;
            String promptText = """
                You are beginning implementation for: %s.
                Follow the constitutional engineering workflow:
                1. MANDATORY CONTEXT RETRIEVAL: Call `get_task_context` and `get_context` to retrieve the task requirements, approved decisions, and blast-radius constraints before substantial work.
                2. Check existing dependencies and contracts via `get_project_dependencies` and `get_api_contract`.
                3. Check affected test files with `get_relevant_tests`.
                4. Implement the feature in small, reviewable steps.
                5. Before finishing, call `record_implementation_result` detailing modified files and unresolved issues (moves task to DONE_UNVERIFIED).
                6. Execute tests and call `record_test_result` with test counts and raw execution logs. Note: A task can only reach VERIFIED when passing test evidence is recorded.
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
        } else if ("coordinate_task".equalsIgnoreCase(name)) {
            String taskId = arguments != null && arguments.get("task_id") != null ? arguments.get("task_id").toString() : "unknown";
            String promptText = """
                You are assigned to coordinate task: %s.
                1. Call `get_task_context(task_id="%s")` to load active decisions, relevant artifacts, and prior attempts.
                2. Inspect open issues and affected areas.
                3. Update task status to IN_PROGRESS using `update_task_status`.
                4. Implement required changes, and run `analyze_change_impact` to verify blast radius.
                5. Call `record_implementation_result` to mark candidate completion (DONE_UNVERIFIED).
                6. Call `record_test_result` to submit test evidence. Only genuine passing test evidence can verify this task.
            """.formatted(taskId, taskId);

            return Map.of(
                "description", "Task Coordination Prompt",
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

