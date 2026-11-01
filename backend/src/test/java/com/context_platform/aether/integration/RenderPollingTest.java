package com.context_platform.aether.integration;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.integration.model.DeploymentRecord;
import com.context_platform.aether.integration.model.IntegrationConfigRecord;
import com.context_platform.aether.integration.model.IntegrationStatus;
import com.context_platform.aether.integration.model.NormalizedEvent;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.provider.render.RenderAdapter;
import com.context_platform.aether.integration.repository.DeploymentRepository;
import com.context_platform.aether.integration.repository.IntegrationCheckpointRepository;
import com.context_platform.aether.integration.repository.IntegrationConfigRepository;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RenderPollingTest extends BaseTest {

    @Autowired
    private RenderAdapter renderAdapter;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private IntegrationCheckpointRepository checkpointRepository;

    @Autowired
    private IntegrationConfigRepository configRepository;

    private MockWebServer mockWebServer;
    private UUID projectId;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        renderAdapter.setRenderApiBaseUrl(mockWebServer.url("/v1").toString());

        AuthResponse user = registerUser("render_poll_" + UUID.randomUUID() + "@example.com", "Password123!", "Render Poller");
        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Render Polling Proj", "Testing Render deploys polling", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
                user.user().id(),
                user.user().email()
        );
        projectId = project.id();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        renderAdapter.setRenderApiBaseUrl("https://api.render.com/v1");
    }

    @Test
    @DisplayName("Real REST WebClient polling consumes deploys, ingests deployments, and updates cursor")
    void testSuccessfulPolling() throws InterruptedException {
        renderAdapter.connect(projectId, Map.of("serviceId", "srv-test-service-123"), "rnd_validapikey1234567890");

        String mockResponseBody = """
            [
              {
                "deploy": {
                  "id": "dep-render-success-01",
                  "commit": {
                    "id": "commit-sha-778899",
                    "message": "fix: critical bug in payment handler"
                  },
                  "status": "live",
                  "createdAt": "2026-10-31T12:00:00Z",
                  "finishedAt": "2026-10-31T12:04:30Z"
                },
                "cursor": "cursor-dep-01"
              }
            ]
            """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockResponseBody));

        List<NormalizedEvent> events = renderAdapter.retrieve(projectId, null, 10);

        assertThat(events).hasSize(1);
        NormalizedEvent event = events.get(0);
        assertThat(event.eventId()).isEqualTo("dep-render-success-01");
        assertThat(event.summary()).contains("live");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/v1/services/srv-test-service-123/deploys?limit=10");
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer rnd_validapikey1234567890");

        // Verify deployment was persisted
        Optional<DeploymentRecord> deployOpt = deploymentRepository.findByExternalDeployId(projectId, "dep-render-success-01");
        assertThat(deployOpt).isPresent();
        DeploymentRecord deploy = deployOpt.get();
        assertThat(deploy.serviceId()).isEqualTo("srv-test-service-123");
        assertThat(deploy.commitSha()).isEqualTo("commit-sha-778899");
        assertThat(deploy.status()).isEqualTo("live");
        assertThat(deploy.failureClassification()).isEqualTo("NONE");

        // Verify checkpoint cursor advanced
        Optional<String> cursor = checkpointRepository.getCursor(projectId, ProviderType.RENDER, "deploys");
        assertThat(cursor).isPresent();
        assertThat(cursor.get()).isEqualTo("cursor-dep-01");
    }

    @Test
    @DisplayName("Polling handles HTTP 429 rate limit gracefully without crashing")
    void testPollingHandlesRateLimit429() {
        renderAdapter.connect(projectId, Map.of("serviceId", "srv-rate-limit"), "rnd_validapikey1234567890");

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "60")
                .setBody("{\"message\":\"Too many requests\"}"));

        List<NormalizedEvent> events = renderAdapter.retrieve(projectId, null, 10);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Polling handles HTTP 401 unauthorized and marks integration status ERROR")
    void testPollingHandlesUnauthorized401() {
        renderAdapter.connect(projectId, Map.of("serviceId", "srv-unauthorized"), "rnd_invalidkey1234567890");

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Invalid API Key\"}"));

        List<NormalizedEvent> events = renderAdapter.retrieve(projectId, null, 10);
        assertThat(events).isEmpty();

        IntegrationConfigRecord config = configRepository.findByProjectIdAndProvider(projectId, ProviderType.RENDER).orElseThrow();
        assertThat(config.status()).isEqualTo(IntegrationStatus.ERROR);
    }

    @Test
    @DisplayName("Polling unconfigured project returns empty list safely")
    void testPollingUnconfiguredProjectReturnsEmpty() {
        List<NormalizedEvent> events = renderAdapter.retrieve(projectId, null, 10);
        assertThat(events).isEmpty();
    }
}
