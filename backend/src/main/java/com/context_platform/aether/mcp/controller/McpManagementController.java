package com.context_platform.aether.mcp.controller;

import com.context_platform.aether.mcp.model.*;
import com.context_platform.aether.mcp.repository.McpCallLogRepository;
import com.context_platform.aether.mcp.service.McpTokenService;
import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/integrations/mcp")
@Tag(name = "MCP Management", description = "Endpoints for managing MCP connection tokens, activity logs, and client configurations")
public class McpManagementController {

    private final McpTokenService tokenService;
    private final McpCallLogRepository callLogRepository;
    private final ProjectSecurityChecker securityChecker;

    public McpManagementController(
        McpTokenService tokenService,
        McpCallLogRepository callLogRepository,
        ProjectSecurityChecker securityChecker
    ) {
        this.tokenService = tokenService;
        this.callLogRepository = callLogRepository;
        this.securityChecker = securityChecker;
    }

    @PostMapping("/tokens")
    @Operation(summary = "Generate a new scoped MCP Bearer token (secret returned ONLY ONCE)")
    public ResponseEntity<CreateMcpTokenResponse> createToken(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateMcpTokenRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "MEMBER");
        return ResponseEntity.ok(tokenService.createToken(projectId, request));
    }

    @GetMapping("/tokens")
    @Operation(summary = "List all MCP tokens for the project with masked prefixes")
    public ResponseEntity<List<McpTokenResponse>> listTokens(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "VIEWER");
        return ResponseEntity.ok(tokenService.listTokens(projectId));
    }

    @PostMapping("/tokens/{tokenId}/revoke")
    @Operation(summary = "Immediately revoke an MCP token")
    public ResponseEntity<Void> revokeToken(
        @PathVariable UUID projectId,
        @PathVariable UUID tokenId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "MEMBER");
        tokenService.revokeToken(tokenId, projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tokens/{tokenId}/rotate")
    @Operation(summary = "Rotate an existing MCP token, invalidating the old one and issuing a new secret")
    public ResponseEntity<CreateMcpTokenResponse> rotateToken(
        @PathVariable UUID projectId,
        @PathVariable UUID tokenId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "MEMBER");
        return ResponseEntity.ok(tokenService.rotateToken(tokenId, projectId));
    }

    @PostMapping("/tokens/{tokenId}/pause")
    @Operation(summary = "Pause or resume an MCP connection without deleting data")
    public ResponseEntity<Void> pauseToken(
        @PathVariable UUID projectId,
        @PathVariable UUID tokenId,
        @RequestParam boolean paused,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "MEMBER");
        tokenService.setPaused(tokenId, projectId, paused);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activity")
    @Operation(summary = "Query MCP call activity logs with pagination and filters")
    public ResponseEntity<List<McpActivityLogResponse>> getActivity(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String tool,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "VIEWER");
        var logs = callLogRepository.findByProjectId(projectId, limit, offset, status, tool);
        return ResponseEntity.ok(logs.stream().map(McpActivityLogResponse::from).toList());
    }

    @GetMapping("/reliability")
    @Operation(summary = "Retrieve real-world agent reliability metrics and session instrumentation")
    public ResponseEntity<McpReliabilityReportResponse> getReliabilityReport(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "VIEWER");
        return ResponseEntity.ok(callLogRepository.calculateReliabilityMetrics(projectId));
    }

    @GetMapping("/config")
    @Operation(summary = "Generate copy-ready client configuration and workspace rules snippets")
    public ResponseEntity<Map<String, Object>> getConfigSnippet(
        @PathVariable UUID projectId,
        @RequestParam McpClientPlatform platform,
        @RequestParam(required = false) String baseUrl,
        @RequestParam(required = false) String token,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal != null ? principal.id() : null, "VIEWER");
        return ResponseEntity.ok(tokenService.generateClientConfig(projectId, platform, baseUrl, token));
    }
}

