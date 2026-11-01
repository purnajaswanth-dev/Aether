package com.context_platform.aether.security;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import com.context_platform.aether.mcp.model.CreateMcpTokenRequest;
import com.context_platform.aether.mcp.model.CreateMcpTokenResponse;
import com.context_platform.aether.mcp.model.McpClientPlatform;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.security.McpSecurityService;
import com.context_platform.aether.mcp.service.McpTokenService;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class McpTokenHardeningTest extends BaseTest {

    @Autowired
    private McpTokenService mcpTokenService;

    @Autowired
    private McpSecurityService mcpSecurityService;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("mcp_sec_" + UUID.randomUUID() + "@example.com", "Password123!", "MCP Tester");
        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("MCP Security Test Proj", "Hardening MCP token auth and scopes", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
                user.user().id(),
                user.user().email()
        );
        projectId = project.id();
    }

    @Test
    @DisplayName("Expired MCP token is rejected with 401 Unauthorized")
    void testExpiredMcpTokenRejected() {
        // Create token with expiration in the past
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        CreateMcpTokenResponse token = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Expired Agent", McpClientPlatform.ANTIGRAVITY, List.of("READ_PROJECT_CONTEXT"), 60, past)
        );

        assertThrows(ApiException.class, () ->
                mcpSecurityService.authenticate("Bearer " + token.rawToken())
        );
    }

    @Test
    @DisplayName("Revoked MCP token is rejected immediately upon revocation")
    void testRevokedMcpTokenRejected() {
        CreateMcpTokenResponse token = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Active Agent", McpClientPlatform.ANTIGRAVITY, List.of("READ_PROJECT_CONTEXT"), 60, null)
        );

        // First authenticate: valid
        McpToken authenticated = mcpSecurityService.authenticate("Bearer " + token.rawToken());
        assertThat(authenticated.tokenName()).isEqualTo("Active Agent");

        // Revoke token
        mcpTokenService.revokeToken(token.id(), projectId);

        // Immediate rejection
        assertThrows(ApiException.class, () ->
                mcpSecurityService.authenticate("Bearer " + token.rawToken())
        );
    }

    @Test
    @DisplayName("Paused MCP token is rejected and can be resumed")
    void testPausedMcpTokenHandling() {
        CreateMcpTokenResponse token = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Pausable Agent", McpClientPlatform.CLAUDE, List.of("READ_PROJECT_CONTEXT"), 60, null)
        );

        // Pause token
        mcpTokenService.setPaused(token.id(), projectId, true);

        // Must reject while paused
        assertThrows(ApiException.class, () ->
                mcpSecurityService.authenticate("Bearer " + token.rawToken())
        );

        // Resume token
        mcpTokenService.setPaused(token.id(), projectId, false);

        // Must succeed when unpaused
        McpToken authenticated = mcpSecurityService.authenticate("Bearer " + token.rawToken());
        assertThat(authenticated.isPaused()).isFalse();
    }

    @Test
    @DisplayName("Token rotation issues new valid raw token and invalidates old token")
    void testTokenRotation() {
        CreateMcpTokenResponse original = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Rotating Agent", McpClientPlatform.CHATGPT, List.of("READ_PROJECT_CONTEXT", "SAVE_ARTIFACTS"), 60, null)
        );

        // Rotate token
        CreateMcpTokenResponse rotated = mcpTokenService.rotateToken(original.id(), projectId);

        assertThat(rotated.id()).isNotEqualTo(original.id());
        assertThat(rotated.rawToken()).isNotEqualTo(original.rawToken());

        // Old token must fail
        assertThrows(ApiException.class, () ->
                mcpSecurityService.authenticate("Bearer " + original.rawToken())
        );

        // New token must succeed
        McpToken authenticated = mcpSecurityService.authenticate("Bearer " + rotated.rawToken());
        assertThat(authenticated.tokenName()).contains("Rotating Agent");
        assertThat(authenticated.permissions()).contains("SAVE_ARTIFACTS");
    }

    @Test
    @DisplayName("Permission scope boundaries are strictly enforced per tool")
    void testPermissionScopeBoundaries() {
        // Token with ONLY READ_PROJECT_CONTEXT
        CreateMcpTokenResponse readOnlyToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Read Only Agent", McpClientPlatform.ANTIGRAVITY, List.of("READ_PROJECT_CONTEXT"), 60, null)
        );

        McpToken token = mcpSecurityService.authenticate("Bearer " + readOnlyToken.rawToken());

        // Calling save_artifact requires SAVE_ARTIFACTS -> must fail
        assertThrows(ProjectAccessDeniedException.class, () ->
                mcpSecurityService.enforcePermission(token, "SAVE_ARTIFACTS")
        );

        // Calling request_action requires EXECUTE_APPROVED_ACTIONS -> must fail
        assertThrows(ProjectAccessDeniedException.class, () ->
                mcpSecurityService.enforcePermission(token, "EXECUTE_APPROVED_ACTIONS")
        );

        // Calling propose_decision requires RECORD_DECISIONS -> must fail
        assertThrows(ProjectAccessDeniedException.class, () ->
                mcpSecurityService.enforcePermission(token, "RECORD_DECISIONS")
        );

        // Calling get_deployment_logs requires READ_DEPLOYMENT_DATA -> must fail
        assertThrows(ProjectAccessDeniedException.class, () ->
                mcpSecurityService.enforcePermission(token, "READ_DEPLOYMENT_DATA")
        );

        // Calling get_project_context requires READ_PROJECT_CONTEXT -> must succeed
        mcpSecurityService.enforcePermission(token, "READ_PROJECT_CONTEXT");
    }
}
