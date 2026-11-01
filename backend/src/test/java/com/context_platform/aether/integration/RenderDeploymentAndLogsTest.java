package com.context_platform.aether.integration;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.integration.model.DeploymentRecord;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.provider.render.RenderAdapter;
import com.context_platform.aether.integration.repository.DeploymentRepository;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RenderDeploymentAndLogsTest extends BaseTest {

    @Autowired
    private RenderAdapter renderAdapter;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Test
    @DisplayName("Render deployment ingestion classifies failures and extracts error lines")
    void testRenderFailureClassification() {
        AuthResponse user = registerUser("render_tester@example.com", "Password123!", "Render Tester");
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Render Ops Proj", "Testing Render deploys", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        renderAdapter.connect(project.id(), Map.of("serviceId", "srv-c8testservice"), "rnd_mocktestapikey12345");

        // 1. Ingest Build Failure
        String buildLogs = """
            ==> Cloning from https://github.com/acme/repo...
            ==> Running build command: npm run build
            npm ERR! code ELIFECYCLE
            npm ERR! errno 1
            npm ERR! acme@1.0.0 build: `tsc && vite build`
            error TS2304: Cannot find name 'UndefinedSymbol'.
            ==> Build failed 😞
            """;

        DeploymentRecord failedBuild = renderAdapter.ingestDeployment(
            project.id(),
            "srv-c8testservice",
            "dep-build-fail-001",
            "production",
            "sha11223344",
            "build_failed",
            buildLogs,
            Instant.now().minusSeconds(120),
            Instant.now()
        );

        assertThat(failedBuild.status()).isEqualTo("build_failed");
        assertThat(failedBuild.failureClassification()).isEqualTo("BUILD_FAILURE");
        assertThat(failedBuild.extractedErrors()).isNotEmpty();
        assertThat(failedBuild.extractedErrors().toString()).contains("npm ERR!");
        assertThat(failedBuild.buildLogsArtifactId()).isNotNull();

        // 2. Ingest Deploy Failure (Health Check timeout)
        String deployLogs = """
            ==> Starting service with 'docker run'
            ==> Service started on port 8080
            ==> Checking service health...
            ==> Timed out waiting for service to respond to HTTP health check on path /actuator/health
            ==> Health check failed. Rolling back.
            """;

        DeploymentRecord failedDeploy = renderAdapter.ingestDeployment(
            project.id(),
            "srv-c8testservice",
            "dep-health-fail-002",
            "production",
            "sha55667788",
            "deploy_failed",
            deployLogs,
            Instant.now().minusSeconds(60),
            Instant.now()
        );

        assertThat(failedDeploy.status()).isEqualTo("deploy_failed");
        assertThat(failedDeploy.failureClassification()).isEqualTo("DEPLOY_FAILURE");

        // 3. Ingest Live Success
        DeploymentRecord liveDeploy = renderAdapter.ingestDeployment(
            project.id(),
            "srv-c8testservice",
            "dep-live-003",
            "production",
            "sha9900aabb",
            "live",
            "==> Deployment succeeded! Service is live at https://srv.onrender.com",
            Instant.now().minusSeconds(30),
            Instant.now()
        );

        assertThat(liveDeploy.status()).isEqualTo("live");
        assertThat(liveDeploy.failureClassification()).isEqualTo("NONE");

        // Query failures
        List<DeploymentRecord> failures = deploymentRepository.findFailures(project.id(), 10);
        assertThat(failures).hasSize(2);
        assertThat(failures).extracting(DeploymentRecord::externalDeployId)
            .containsExactly("dep-health-fail-002", "dep-build-fail-001");
    }
}
