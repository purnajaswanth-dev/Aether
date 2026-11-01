package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionActionRequest;
import com.context_platform.aether.decision.DecisionState;
import com.context_platform.aether.integration.model.DeploymentRecord;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.repository.DeploymentRepository;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.*;
import com.context_platform.aether.workflow.model.dto.*;
import com.context_platform.aether.workflow.service.AgentTrackingService;
import com.context_platform.aether.workflow.service.DebuggingService;
import com.context_platform.aether.workflow.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class Phase5WorkflowCoordinationEndToEndTest extends BaseTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private AgentTrackingService agentTrackingService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private DebuggingService debuggingService;

    @Test
    void testFullEndToEndWorkflowCoordinationScenario() {
        AuthResponse user = registerUser("lead_engineer@vionex.io", "Pass1234!", "Lead Engineer");
        UUID humanUserId = user.user().id();

        // 1. Create Project
        ProjectResponse project = createTestProject("Vionex Payments", humanUserId);
        UUID projectId = project.id();

        // 2. Client A (Claude) saves initial Architecture Document
        String archDoc = "# Vionex Architecture\nHexagonal modular monolith with PostgreSQL.";
        var archArtifact = artifactService.createArtifact(
                projectId,
                new CreateArtifactRequest("System Architecture", "ARCHITECTURE", "CLAUDE", "arch.md", archDoc, "Initial draft"),
                humanUserId
        );
        assertNotNull(archArtifact.id());

        // 3. Human creates & approves ADR
        var decision = decisionService.createDecision(
                projectId,
                new CreateDecisionRequest(
                        "ADR-001 Use AES-256-GCM for Token Encryption",
                        "SECURITY",
                        "Must protect tokens against eavesdropping",
                        "AES-256-GCM authenticated cipher selected",
                        "Provides both encryption and integrity authentication",
                        "Requires 12-byte nonce uniqueness",
                        null
                ),
                humanUserId
        );
        var underReview = decisionService.submitForReview(projectId, decision.id(), new DecisionActionRequest(decision.lockVersion(), "Ready for review", null, null), humanUserId);
        decisionService.approveDecision(projectId, decision.id(), new DecisionActionRequest(underReview.lockVersion(), "Approved", null, null), humanUserId);

        // 4. Client B (Antigravity) starts session and reviews architecture
        AgentSessionRecord session = agentTrackingService.startSession(projectId, "ANTIGRAVITY", null);
        assertNotNull(session.id());

        // 5. Task created and linked to Decision and Architecture
        TaskRecord task = taskService.createTask(
                projectId,
                new CreateTaskRequest("Implement Token Encryption", "Apply AES-256-GCM", TaskPriority.HIGH, humanUserId, false, null),
                humanUserId
        );
        taskService.linkTarget(projectId, task.id(), TaskLinkType.DECISION, decision.id().toString(), "ADR-001");
        taskService.linkTarget(projectId, task.id(), TaskLinkType.ARTIFACT, archArtifact.id().toString(), "System Architecture");

        // 6. Coding agent retrieves context using get_task_context
        TaskContextResponse ctx = taskService.getTaskContext(projectId, task.id());
        assertEquals("Implement Token Encryption", ctx.task().title());
        assertFalse(ctx.linkedDecisions().isEmpty());
        assertFalse(ctx.relevantArtifacts().isEmpty());

        // 7. Coding agent records implementation -> task moves to DONE_UNVERIFIED
        task = taskService.updateTaskStatus(projectId, task.id(), new UpdateTaskStatusRequest(
                TaskStatus.IN_PROGRESS, task.lockVersion(), "Agent began coding", null, ActorType.AGENT, "Antigravity"
        ));

        ImplementationRecord implRecord = agentTrackingService.recordImplementation(
                projectId,
                task.id(),
                new RecordImplementationRequest(
                        "Implemented TokenEncryptionService with GCM cipher",
                        List.of("TokenEncryptionService.java", "KeyProvider.java"),
                        List.of("Needs integration tests with KMS"),
                        session.id(),
                        "Antigravity"
                )
        );
        assertEquals("DONE_UNVERIFIED", implRecord.candidateStatus());

        TaskRecord taskAfterImpl = taskService.getTask(projectId, task.id()).orElseThrow();
        assertEquals(TaskStatus.DONE_UNVERIFIED, taskAfterImpl.status(), "Implementation must move task to DONE_UNVERIFIED");

        // 8. Commit linked & Initial Deployment FAILS
        taskService.linkTarget(projectId, task.id(), TaskLinkType.COMMIT, "git-sha-7a8b9c", "feat: token encryption service");

        String failLogs = "2026-10-31 22:00:00 [main] ERROR com.app.Config - InvalidKeyException: Illegal key size";
        var failLogArt = artifactService.createArtifact(
                projectId,
                new CreateArtifactRequest("Deploy 1 Logs", "LOGS", "RENDER", "deploy-fail.log", failLogs, "Failed deploy logs"),
                humanUserId
        );

        DeploymentRecord failedDeploy = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-vionex", "dep-fail-01", "production",
                "git-sha-7a8b9c", "FAILED", "CONFIG_ERROR", failLogArt.id(), null,
                List.of("InvalidKeyException: Illegal key size"), task.id(),
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(90), Instant.now()
        ));

        // 9. Agent retrieves debugging package without manual log copying
        Map<String, Object> debugPkg = debuggingService.assembleDebuggingPackage(projectId, task.id(), failedDeploy.id(), FailureClassification.CONFIG_ERROR);
        assertNotNull(debugPkg);
        assertEquals("dep-fail-01", debugPkg.get("deploymentId"));
        assertTrue(((String) debugPkg.get("isolatedLogs")).contains("InvalidKeyException"));

        // 10. Agent fixes code and records genuine passing test execution
        TestExecutionRecord testExec = agentTrackingService.recordTestExecution(
                projectId,
                task.id(),
                new RecordTestExecutionRequest(
                        "INTEGRATION", "JUNIT5", true, 15, 15, 0, 1850L,
                        null, "Tests run: 15, Failures: 0, Errors: 0", session.id(), "AntigravityRunner"
                )
        );
        assertTrue(testExec.passed());

        // 11. Task automatically verified through passing test evidence
        TaskRecord verifiedTask = taskService.getTask(projectId, task.id()).orElseThrow();
        assertEquals(TaskStatus.VERIFIED, verifiedTask.status(), "Genuine test execution evidence verified the task");

        // 12. Subsequent deployment succeeds and resolves debugging records
        DeploymentRecord successDeploy = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-vionex", "dep-success-02", "production",
                "git-sha-fixed-99", "LIVE", "NONE", null, null,
                List.of(), task.id(),
                Instant.now().minusSeconds(30), Instant.now(), Instant.now()
        ));

        debuggingService.onDeploymentSuccess(projectId, successDeploy.id(), "git-sha-fixed-99");

        // Verify connected history
        TaskContextResponse finalContext = taskService.getTaskContext(projectId, task.id());
        assertEquals(TaskStatus.VERIFIED, finalContext.task().status());
        assertNotNull(finalContext.latestImplementation());
        assertFalse(finalContext.priorAttempts().isEmpty());
    }
}
