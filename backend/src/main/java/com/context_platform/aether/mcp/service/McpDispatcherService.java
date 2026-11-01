package com.context_platform.aether.mcp.service;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import com.context_platform.aether.event.AuditService;
import com.context_platform.aether.mcp.model.*;
import com.context_platform.aether.mcp.repository.McpCallLogRepository;
import com.context_platform.aether.mcp.security.McpSecurityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class McpDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(McpDispatcherService.class);

    private final McpToolRegistry toolRegistry;
    private final McpResourceService resourceService;
    private final McpPromptService promptService;
    private final McpCallLogRepository callLogRepository;
    private final AuditService auditService;
    private final McpSecurityService securityService;
    private final ObjectMapper objectMapper;

    public McpDispatcherService(
        McpToolRegistry toolRegistry,
        McpResourceService resourceService,
        McpPromptService promptService,
        McpCallLogRepository callLogRepository,
        AuditService auditService,
        McpSecurityService securityService,
        ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.resourceService = resourceService;
        this.promptService = promptService;
        this.callLogRepository = callLogRepository;
        this.auditService = auditService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    public JsonRpcResponse dispatch(JsonRpcRequest request, McpToken token) {
        long startTime = System.currentTimeMillis();
        String method = request.method() != null ? request.method() : "";
        String toolName = method;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            // Establish security and RLS tenant context
            securityService.setupSecurityContext(token);

            Object result = switch (method) {
                case "initialize" -> handleInitialize(request);
                case "notifications/initialized" -> Map.of();
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools", toolRegistry.listToolDefinitions());
                case "tools/call" -> {
                    String calledTool = extractToolName(request);
                    toolName = calledTool != null ? calledTool : "unknown_tool";
                    yield handleToolCall(request, token);
                }
                case "resources/list" -> Map.of("resources", resourceService.listResources(token));
                case "resources/read" -> {
                    toolName = "resources/read";
                    yield handleResourceRead(request, token);
                }
                case "prompts/list" -> Map.of("prompts", promptService.listPrompts(token));
                case "prompts/get" -> {
                    toolName = "prompts/get";
                    yield handlePromptGet(request, token);
                }
                default -> throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Method not found: " + method);
            };

            return JsonRpcResponse.success(request.id(), result);

        } catch (ProjectAccessDeniedException e) {
            status = "UNAUTHORIZED";
            errorMessage = e.getMessage();
            log.warn("MCP Access Denied [tool={}, token={}]: {}", toolName, token.id(), e.getMessage());
            return JsonRpcResponse.error(request.id(), JsonRpcError.FORBIDDEN, e.getMessage());

        } catch (ApiException e) {
            status = e.getStatus().is4xxClientError() ? "UNAUTHORIZED" : "ERROR";
            errorMessage = e.getMessage();
            int code = e.getStatus().value() == 429 ? JsonRpcError.RATE_LIMITED : JsonRpcError.INTERNAL_ERROR;
            return JsonRpcResponse.error(request.id(), code, e.getMessage());

        } catch (Exception e) {
            status = "ERROR";
            errorMessage = e.getMessage();
            log.error("MCP Internal Error [tool={}, token={}]: {}", toolName, token.id(), e.getMessage(), e);
            return JsonRpcResponse.error(request.id(), JsonRpcError.INTERNAL_ERROR, "Internal server error: " + e.getMessage());

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            securityService.clearSecurityContext();

            // Record call log
            try {
                callLogRepository.save(new McpCallLog(
                    UUID.randomUUID(),
                    token.projectId(),
                    token.id(),
                    toolName,
                    token.clientPlatform(),
                    duration,
                    status,
                    errorMessage,
                    null
                ));
            } catch (Exception ex) {
                log.warn("Failed to write MCP call log: {}", ex.getMessage());
            }

            // Record audit event
            try {
                String auditDetails = objectMapper.writeValueAsString(Map.of(
                    "tool", toolName,
                    "platform", token.clientPlatform().name(),
                    "token_prefix", token.tokenPrefix(),
                    "duration_ms", duration,
                    "status", status
                ));
                auditService.log(
                    token.projectId(),
                    null,
                    "MCP_INVOCATION:" + toolName,
                    "AGENT",
                    "MCP",
                    token.clientPlatform().name(),
                    auditDetails
                );
            } catch (Exception ex) {
                log.warn("Failed to write MCP audit log: {}", ex.getMessage());
            }
        }
    }

    private Object handleInitialize(JsonRpcRequest request) {
        String clientProtocol = "2024-11-05";
        if (request.params() != null && request.params().get("protocolVersion") != null) {
            clientProtocol = request.params().get("protocolVersion").toString();
        }

        return Map.of(
            "protocolVersion", clientProtocol,
            "capabilities", Map.of(
                "tools", Map.of("listChanged", false),
                "resources", Map.of("subscribe", false, "listChanged", false),
                "prompts", Map.of("listChanged", false)
            ),
            "serverInfo", Map.of(
                "name", "aether-context-platform",
                "version", "1.0.0"
            )
        );
    }

    @SuppressWarnings("unchecked")
    private String extractToolName(JsonRpcRequest request) {
        if (request.params() != null && request.params().get("name") != null) {
            return request.params().get("name").toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object handleToolCall(JsonRpcRequest request, McpToken token) {
        if (request.params() == null || request.params().get("name") == null) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Tool name is required in tools/call");
        }

        String name = request.params().get("name").toString();
        Map<String, Object> arguments = Map.of();
        if (request.params().get("arguments") instanceof Map<?, ?> argMap) {
            arguments = (Map<String, Object>) argMap;
        }

        Object result = toolRegistry.executeTool(name, arguments, token);
        String textOutput;
        try {
            if (result instanceof String str) {
                textOutput = str;
            } else {
                textOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            }
        } catch (JsonProcessingException e) {
            textOutput = result.toString();
        }

        return Map.of(
            "content", List.of(
                Map.of(
                    "type", "text",
                    "text", textOutput
                )
            ),
            "isError", false
        );
    }

    private Object handleResourceRead(JsonRpcRequest request, McpToken token) {
        if (request.params() == null || request.params().get("uri") == null) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "uri is required for resources/read");
        }
        String uri = request.params().get("uri").toString();
        return resourceService.readResource(uri, token);
    }

    @SuppressWarnings("unchecked")
    private Object handlePromptGet(JsonRpcRequest request, McpToken token) {
        if (request.params() == null || request.params().get("name") == null) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "name is required for prompts/get");
        }
        String name = request.params().get("name").toString();
        Map<String, Object> arguments = Map.of();
        if (request.params().get("arguments") instanceof Map<?, ?> argMap) {
            arguments = (Map<String, Object>) argMap;
        }
        return promptService.getPrompt(name, arguments, token);
    }
}

