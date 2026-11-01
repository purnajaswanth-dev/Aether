package com.context_platform.aether.integration;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import com.context_platform.aether.integration.model.AgentDeviceRecord;
import com.context_platform.aether.integration.model.AgentFileMetadataRecord;
import com.context_platform.aether.integration.provider.render.RenderAdapter;
import com.context_platform.aether.integration.repository.AgentDeviceRepository;
import com.context_platform.aether.integration.repository.AgentFileMetadataRepository;
import com.context_platform.aether.mcp.model.CreateMcpTokenRequest;
import com.context_platform.aether.mcp.model.CreateMcpTokenResponse;
import com.context_platform.aether.mcp.model.McpClientPlatform;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.repository.McpTokenRepository;
import com.context_platform.aether.mcp.service.McpTokenService;
import com.context_platform.aether.mcp.service.McpToolRegistry;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpDeploymentToolsTest extends BaseTest {

    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private McpTokenService tokenService;

    @Autowired
    private McpTokenRepository tokenRepository;

    @Autowired
    private RenderAdapter renderAdapter;

    @Autowired
    private AgentDeviceRepository deviceRepository;

    @Autowired
    private AgentFileMetadataRepository fileMetadataRepository;

    @Test
    @DisplayName("MCP deployment tools retrieve deployment status, failures, and enforce READ_DEPLOYMENT_DATA")
    void testMcpDeploymentTools() {
        AuthResponse user = registerUser("mcp_deploy@example.com", "Password123!", "MCP Deployer");
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("MCP Deploy Proj", "Testing deployment MCP tools", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        // Ingest dummy deployments
        renderAdapter.ingestDeployment(
            project.id(),
            "srv-mcp",
            "dep-mcp-fail-1",
            "production",
            "sha1111",
            "build_failed",
            "Error: compilation failed on line 42",
            Instant.now().minusSeconds(100),
            Instant.now().minusSeconds(90)
        );

        renderAdapter.ingestDeployment(
            project.id(),
            "srv-mcp",
            "dep-mcp-live-2",
            "production",
            "sha2222",
            "live",
            "Deployment live at https://mcp.render.com",
            Instant.now().minusSeconds(60),
            Instant.now().minusSeconds(50)
        );

        // 1. Create token WITH READ_DEPLOYMENT_DATA permission
        CreateMcpTokenResponse tokenWithPerm = tokenService.createToken(
            project.id(),
            new CreateMcpTokenRequest(
                "Claude with Deployments",
                McpClientPlatform.CLAUDE,
                List.of("READ_PROJECT_CONTEXT", "READ_DEPLOYMENT_DATA"),
                60,
                null
            )
        );
        McpToken mcpTokenWithPerm = tokenRepository.findById(tokenWithPerm.id()).orElseThrow();

        // Test get_latest_deployment
        @SuppressWarnings("unchecked")
        Map<String, Object> latest = (Map<String, Object>) toolRegistry.executeTool("get_latest_deployment", Map.of(), mcpTokenWithPerm);
        assertThat(latest.get("external_deploy_id")).isEqualTo("dep-mcp-live-2");
        assertThat(latest.get("status")).isEqualTo("live");

        // Test get_deployment_status
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) toolRegistry.executeTool(
            "get_deployment_status",
            Map.of("deploy_id", "dep-mcp-fail-1"),
            mcpTokenWithPerm
        );
        assertThat(status.get("status")).isEqualTo("build_failed");
        assertThat(status.get("failure_classification")).isEqualTo("BUILD_FAILURE");

        // Test get_previous_failures
        @SuppressWarnings("unchecked")
        Map<String, Object> failures = (Map<String, Object>) toolRegistry.executeTool(
            "get_previous_failures",
            Map.of("limit", 5),
            mcpTokenWithPerm
        );
        assertThat((int) failures.get("count")).isEqualTo(1);

        // Test get_deployment_logs WITH permission -> succeeds
        @SuppressWarnings("unchecked")
        Map<String, Object> logsResult = (Map<String, Object>) toolRegistry.executeTool(
            "get_deployment_logs",
            Map.of("deploy_id", "dep-mcp-fail-1"),
            mcpTokenWithPerm
        );
        assertThat(logsResult.get("logs").toString()).contains("compilation failed on line 42");
        assertThat(logsResult.get("logs").toString()).contains("<<<UNTRUSTED_DATA_BOUNDARY");

        // 2. Create token WITHOUT READ_DEPLOYMENT_DATA permission
        CreateMcpTokenResponse tokenWithoutPerm = tokenService.createToken(
            project.id(),
            new CreateMcpTokenRequest(
                "ChatGPT without Deployments",
                McpClientPlatform.CHATGPT,
                List.of("READ_PROJECT_CONTEXT"), // lacks READ_DEPLOYMENT_DATA
                60,
                null
            )
        );
        McpToken mcpTokenWithoutPerm = tokenRepository.findById(tokenWithoutPerm.id()).orElseThrow();

        // Test get_deployment_logs WITHOUT permission -> throws ProjectAccessDeniedException
        assertThatThrownBy(() -> toolRegistry.executeTool(
            "get_deployment_logs",
            Map.of("deploy_id", "dep-mcp-fail-1"),
            mcpTokenWithoutPerm
        )).isInstanceOf(ProjectAccessDeniedException.class);
    }

    @Test
    @DisplayName("get_relevant_files retrieves local-agent indexed files with fallback to artifacts")
    void testGetRelevantFiles() {
        AuthResponse user = registerUser("agent_files@example.com", "Password123!", "Agent Files User");
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Agent Files Proj", "Testing file search", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        CreateMcpTokenResponse tokenResp = tokenService.createToken(
            project.id(),
            new CreateMcpTokenRequest(
                "Antigravity Token",
                McpClientPlatform.ANTIGRAVITY,
                List.of("READ_PROJECT_CONTEXT"),
                60,
                null
            )
        );
        McpToken token = tokenRepository.findById(tokenResp.id()).orElseThrow();

        // Register dummy device and indexed file
        AgentDeviceRecord device = deviceRepository.createDevicePairing(
            project.id(),
            "dc_test_" + UUID.randomUUID().toString().replace("-", ""),
            "TEST-1234",
            Instant.now().plusSeconds(900)
        );

        fileMetadataRepository.upsert(new AgentFileMetadataRecord(
            null,
            project.id(),
            device.id(),
            "src/main/java/com/example/PaymentController.java",
            "hash123",
            2048,
            Instant.now(),
            "Java",
            List.of("PaymentController", "processPayment"),
            Instant.now()
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> searchResult = (Map<String, Object>) toolRegistry.executeTool(
            "get_relevant_files",
            Map.of("query", "Payment"),
            token
        );

        assertThat(searchResult.get("source")).isEqualTo("LOCAL_AGENT_INDEX");
        assertThat((int) searchResult.get("count")).isEqualTo(1);
    }
}
