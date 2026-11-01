package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.integration.model.DeploymentRecord;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.repository.DeploymentRepository;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.*;
import com.context_platform.aether.workflow.model.dto.CreateTaskRequest;
import com.context_platform.aether.workflow.repository.DebuggingRecordRepository;
import com.context_platform.aether.workflow.service.DebuggingService;
import com.context_platform.aether.workflow.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DebuggingResolutionServiceScopeTest extends BaseTest {

    @Autowired
    private DebuggingService debuggingService;

    @Autowired
    private DebuggingRecordRepository debuggingRecordRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private TaskService taskService;

    private UUID projectId;
    private UUID userId;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("scope_tester@aether.test", "SecurePass123!", "Scope Tester");
        userId = user.user().id();
        ProjectResponse project = createTestProject("Scope Test Project", userId);
        projectId = project.id();

        TaskRecord task = taskService.createTask(
                projectId,
                new CreateTaskRequest("Fix Service A Bug", "Crash remediation", TaskPriority.HIGH, userId, false, null),
                userId
        );
        taskId = task.id();
    }

    @Test
    @DisplayName("Service A failure remains open when Service B deploys successfully")
    void testDifferentServiceDoesNotResolveFailure() {
        // 1. Service A fails in production
        DeploymentRecord failedServiceA = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-service-a", "dep-fail-a1", "production",
                "sha-a1", "FAILED", "RUNTIME_CRASH", null, null,
                List.of("NullPointerException"), taskId,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), Instant.now()
        ));

        debuggingService.assembleDebuggingPackage(projectId, taskId, failedServiceA.id(), FailureClassification.RUNTIME_CRASH);
        List<DebuggingRecord> openA = debuggingRecordRepository.findByFailedDeploymentId(projectId, failedServiceA.id());
        assertEquals(1, openA.size());
        assertEquals(DebuggingStatus.INVESTIGATING, openA.get(0).status());

        // 2. Service B succeeds in production
        DeploymentRecord successServiceB = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-service-b", "dep-success-b1", "production",
                "sha-b1", "LIVE", "NONE", null, null,
                List.of(), null,
                Instant.now().minusSeconds(30), Instant.now(), Instant.now()
        ));

        debuggingService.onDeploymentSuccess(projectId, successServiceB.id(), "sha-b1");

        // Service A record must STILL be INVESTIGATING (not resolved by Service B)
        DebuggingRecord afterB = debuggingRecordRepository.findById(projectId, openA.get(0).id()).orElseThrow();
        assertEquals(DebuggingStatus.INVESTIGATING, afterB.status(), "Service B success must NOT resolve Service A crash record");
        assertNull(afterB.resolvedDeploymentId());
    }

    @Test
    @DisplayName("Production failure is not resolved by a staging deployment")
    void testDifferentEnvironmentDoesNotResolveFailure() {
        // 1. Service A fails in production
        DeploymentRecord failedProd = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-service-a", "dep-fail-prod-1", "production",
                "sha-prod-1", "FAILED", "CONFIG_ERROR", null, null,
                List.of("ConfigMissingException"), taskId,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), Instant.now()
        ));

        debuggingService.assembleDebuggingPackage(projectId, taskId, failedProd.id(), FailureClassification.CONFIG_ERROR);
        DebuggingRecord rec = debuggingRecordRepository.findByFailedDeploymentId(projectId, failedProd.id()).get(0);

        // 2. Service A succeeds in staging
        DeploymentRecord successStaging = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-service-a", "dep-success-stage-1", "staging",
                "sha-stage-1", "LIVE", "NONE", null, null,
                List.of(), null,
                Instant.now().minusSeconds(30), Instant.now(), Instant.now()
        ));

        debuggingService.onDeploymentSuccess(projectId, successStaging.id(), "sha-stage-1");

        // Production record must remain INVESTIGATING
        DebuggingRecord afterStaging = debuggingRecordRepository.findById(projectId, rec.id()).orElseThrow();
        assertEquals(DebuggingStatus.INVESTIGATING, afterStaging.status(), "Staging deploy must NOT resolve production failure");
    }

    @Test
    @DisplayName("Matching successful deployment on same service and environment resolves the incident")
    void testMatchingDeployResolvesFailure() {
        // 1. Service A fails in production
        DeploymentRecord failedProd = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-service-a", "dep-fail-match", "production",
                "sha-err", "FAILED", "BUILD_FAILURE", null, null,
                List.of("Compilation error"), taskId,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), Instant.now()
        ));

        debuggingService.assembleDebuggingPackage(projectId, taskId, failedProd.id(), FailureClassification.BUILD_FAILURE);
        DebuggingRecord rec = debuggingRecordRepository.findByFailedDeploymentId(projectId, failedProd.id()).get(0);

        // 2. Service A succeeds in production
        DeploymentRecord successProd = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-service-a", "dep-success-match", "production",
                "sha-fixed-100", "LIVE", "NONE", null, null,
                List.of(), null,
                Instant.now().minusSeconds(20), Instant.now(), Instant.now()
        ));

        debuggingService.onDeploymentSuccess(projectId, successProd.id(), "sha-fixed-100");

        // Must be resolved and record the resolving deployment ID
        DebuggingRecord resolved = debuggingRecordRepository.findById(projectId, rec.id()).orElseThrow();
        assertEquals(DebuggingStatus.RESOLVED, resolved.status(), "Matching service and environment deploy must resolve record");
        assertEquals(successProd.id(), resolved.resolvedDeploymentId());
        assertNotNull(resolved.resolvedAt());
        assertTrue(resolved.rootCauseAnalysis().contains("dep-success-match"));
        assertTrue(resolved.rootCauseAnalysis().contains("srv-service-a"));
    }

    @Test
    @DisplayName("Deployment from another project cannot resolve the incident")
    void testCrossProjectDeploymentCannotResolve() {
        // 1. Service A fails in Project 1
        DeploymentRecord failedP1 = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-shared-name", "dep-fail-p1", "production",
                "sha-p1", "FAILED", "RUNTIME_CRASH", null, null,
                List.of("OutOfMemoryError"), taskId,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), Instant.now()
        ));
        debuggingService.assembleDebuggingPackage(projectId, taskId, failedP1.id(), FailureClassification.RUNTIME_CRASH);
        DebuggingRecord rec = debuggingRecordRepository.findByFailedDeploymentId(projectId, failedP1.id()).get(0);

        // 2. Create Project 2 with a successful deployment
        ProjectResponse project2 = createTestProject("Project 2", userId);
        DeploymentRecord successP2 = deploymentRepository.save(new DeploymentRecord(
                null, project2.id(), ProviderType.RENDER, "srv-shared-name", "dep-success-p2", "production",
                "sha-p2", "LIVE", "NONE", null, null,
                List.of(), null,
                Instant.now().minusSeconds(20), Instant.now(), Instant.now()
        ));

        // Call onDeploymentSuccess for Project 1 with successP2 id (from Project 2)
        debuggingService.onDeploymentSuccess(projectId, successP2.id(), "sha-p2");

        // Record in Project 1 must remain INVESTIGATING
        DebuggingRecord stillOpen = debuggingRecordRepository.findById(projectId, rec.id()).orElseThrow();
        assertEquals(DebuggingStatus.INVESTIGATING, stillOpen.status(), "Cross-project deployment must never resolve records");
    }
}

