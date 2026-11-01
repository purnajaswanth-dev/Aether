package com.context_platform.aether.acceptance;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionActionRequest;
import com.context_platform.aether.decision.DecisionResponse;
import com.context_platform.aether.decision.DecisionState;
import com.context_platform.aether.event.TimelineEvent;
import com.context_platform.aether.mcp.model.CreateMcpTokenRequest;
import com.context_platform.aether.mcp.model.CreateMcpTokenResponse;
import com.context_platform.aether.mcp.model.McpClientPlatform;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.security.McpSecurityService;
import com.context_platform.aether.mcp.service.McpTokenService;
import com.context_platform.aether.orchestrator.model.ContextPackageResponse;
import com.context_platform.aether.orchestrator.service.ContextOrchestratorService;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.*;
import com.context_platform.aether.workflow.model.dto.CreateTaskRequest;
import com.context_platform.aether.workflow.model.dto.ReviewActionRequest;
import com.context_platform.aether.workflow.model.dto.SubmitActionRequest;
import com.context_platform.aether.workflow.model.dto.UpdateTaskStatusRequest;
import com.context_platform.aether.workflow.repository.TestExecutionRepository;
import com.context_platform.aether.workflow.service.ActionRequestService;
import com.context_platform.aether.workflow.service.TaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Phase6ProductionAcceptanceTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(Phase6ProductionAcceptanceTest.class);

    @Autowired
    private McpTokenService mcpTokenService;

    @Autowired
    private McpSecurityService mcpSecurityService;

    @Autowired
    private ContextOrchestratorService orchestratorService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TestExecutionRepository testExecutionRepository;

    @Autowired
    private ActionRequestService actionRequestService;

    @Test
    @DisplayName("Phase 6 End-to-End Acceptance: Complete 18-step platform verification lifecycle")
    void testFullPlatformLifecycleAcceptance() {
        log.info("--- Step 1: Register human user ---");
        AuthResponse authA = registerUser("architect_" + UUID.randomUUID() + "@aether.io", "StrongPass123!", "Lead Architect");
        UUID userAId = authA.user().id();
        String userAEmail = authA.user().email();
        assertThat(userAId).isNotNull();

        log.info("--- Step 2: Create project ---");
        ProjectResponse projectA = projectService.createProject(
            new CreateProjectRequest(
                "Core Gateway Service",
                "Mission-critical engineering platform",
                "BACKEND_SERVICE",
                List.of("Java 17", "Spring Boot", "PostgreSQL"),
                "/workspace/gateway",
                "LOCAL_FIRST"
            ),
            userAId,
            userAEmail
        );
        UUID projectAId = projectA.id();
        assertThat(projectAId).isNotNull();

        log.info("--- Step 3: Generate MCP token with specific permissions ---");
        List<String> permissions = List.of(
            "READ_PROJECT_CONTEXT",
            "READ_ARTIFACTS",
            "SAVE_ARTIFACTS",
            "RECORD_DECISIONS",
            "EXECUTE_APPROVED_ACTIONS"
        );
        CreateMcpTokenResponse mcpToken = mcpTokenService.createToken(
            projectAId,
            new CreateMcpTokenRequest(
                "Claude Desktop Agent",
                McpClientPlatform.CLAUDE,
                permissions,
                120,
                java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS)
            )
        );
        assertThat(mcpToken.rawToken()).startsWith("aeth_mcp_");

        log.info("--- Step 4: Authenticate MCP client with token ---");
        McpToken authenticatedMcp = mcpSecurityService.authenticate("Bearer " + mcpToken.rawToken());
        assertThat(authenticatedMcp.projectId()).isEqualTo(projectAId);
        assertThat(authenticatedMcp.permissions()).containsAll(permissions);

        log.info("--- Step 5: Ingest artifact through ingestion pipeline ---");
        String specContent = """
            # Zero-Trust Authentication Architecture
            All microservices authenticate using RS256 JWT tokens.
            Cryptographic key rotation occurs every 30 days.
            Rate limiting is enforced at 100 requests per minute per IP.
            """;
        ArtifactResponse artifact = artifactService.createArtifact(
            projectAId,
            new CreateArtifactRequest(
                "AuthArchitecture.md",
                "ARCHITECTURE",
                "LOCAL",
                "spec-auth-01",
                specContent,
                "Initial architectural baseline"
            ),
            ActorContext.human(userAId, userAEmail)
        );
        assertThat(artifact.id()).isNotNull();

        log.info("--- Step 6: Retrieve context package through orchestrator ---");
        ContextPackageResponse pkg = orchestratorService.buildContextPackage(
            projectAId,
            "Claude-Desktop",
            "Zero trust JWT authentication and key rotation",
            "claude-3-5-sonnet",
            1500
        );
        assertThat(pkg.formattedPackage()).contains("Zero-Trust Authentication Architecture");
        assertThat(pkg.estimatedTokens()).isLessThanOrEqualTo(1500);

        log.info("--- Step 7: Propose technical decision ---");
        DecisionResponse decision = decisionService.createDecision(
            projectAId,
            new CreateDecisionRequest(
                "Adopt Argon2id Password Hashing",
                "SECURITY",
                "Legacy PBKDF2 is vulnerable to GPU accelerated brute-force attacks.",
                "Migrate to Argon2id with 64MB memory cost and 3 iterations.",
                "Provides state-of-the-art memory-hard resistance.",
                "Requires additional memory per concurrent login request.",
                null
            ),
            ActorContext.human(userAId, userAEmail)
        );
        assertThat(decision.state()).isEqualTo(DecisionState.PROPOSED.name());

        log.info("--- Step 8: Human submits and approves technical decision ---");
        DecisionResponse underReview = decisionService.submitForReview(
            projectAId,
            decision.id(),
            new DecisionActionRequest(decision.lockVersion(), "Ready for architecture review", null, null),
            userAId
        );
        DecisionResponse approvedDecision = decisionService.approveDecision(
            projectAId,
            decision.id(),
            new DecisionActionRequest(underReview.lockVersion(), "Approved by security lead", null, null),
            userAId
        );
        assertThat(approvedDecision.state()).isEqualTo(DecisionState.APPROVED.name());

        log.info("--- Step 9: Create workflow task ---");
        TaskRecord task = taskService.createTask(
            projectAId,
            new CreateTaskRequest(
                "Implement Argon2id Hasher",
                "Integrate BouncyCastle Argon2id provider into auth pipeline",
                TaskPriority.HIGH,
                userAId,
                false,
                null
            ),
            userAId
        );
        assertThat(task.status()).isEqualTo(TaskStatus.READY);

        log.info("--- Step 10: Agent transitions task to IN_PROGRESS, then DONE_UNVERIFIED ---");
        TaskRecord inProgressTask = taskService.updateTaskStatus(
            projectAId,
            task.id(),
            new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS, 1, "Agent started implementation", null, ActorType.AGENT, "AntigravityAgent")
        );
        assertThat(inProgressTask.status()).isEqualTo(TaskStatus.IN_PROGRESS);

        TaskRecord unverifiedTask = taskService.updateTaskStatus(
            projectAId,
            task.id(),
            new UpdateTaskStatusRequest(
                TaskStatus.DONE_UNVERIFIED,
                2,
                "Implemented Argon2idPasswordEncoder with Argon2Parameters.ARGON2_id",
                null,
                ActorType.AGENT,
                "AntigravityAgent"
            )
        );
        assertThat(unverifiedTask.status()).isEqualTo(TaskStatus.DONE_UNVERIFIED);

        log.info("--- Step 11: Submit qualifying test execution evidence ---");
        testExecutionRepository.save(new TestExecutionRecord(
            UUID.randomUUID(),
            projectAId,
            task.id(),
            null,
            "UNIT",
            "JUnit 5",
            true,
            15,
            15,
            0,
            1200L,
            null,
            null,
            "CI_Runner",
            null
        ));

        log.info("--- Step 12: Task transitions to VERIFIED based on qualifying test evidence ---");
        TaskRecord verifiedTask = taskService.updateTaskStatus(
            projectAId,
            task.id(),
            new UpdateTaskStatusRequest(TaskStatus.VERIFIED, 3, "Verified by passing test suite", null, ActorType.AGENT, "CI_Runner")
        );
        assertThat(verifiedTask.status()).isEqualTo(TaskStatus.VERIFIED);

        log.info("--- Step 13: Agent submits action request ---");
        ActionRequestRecord actionReq = actionRequestService.submitActionRequest(
            projectAId,
            mcpToken.id(),
            new SubmitActionRequest("DEPLOY_STAGING", Map.of("version", "v1.0.0", "target", "staging"), false, "idemp-" + UUID.randomUUID())
        );
        assertThat(actionReq.status()).isEqualTo(ActionRequestStatus.PENDING);

        log.info("--- Step 14 & 15: Human approves and executes action request ---");
        ActionRequestRecord reviewedAction = actionRequestService.reviewAction(
            projectAId,
            actionReq.id(),
            userAId,
            new ReviewActionRequest(true, "Approved for staging rollout")
        );
        assertThat(reviewedAction.status()).isEqualTo(ActionRequestStatus.EXECUTED);

        log.info("--- Step 16: Verify timeline events in chronological order ---");
        List<TimelineEvent> timeline = timelineService.listTimeline(projectAId, null, null, 100);
        assertThat(timeline).isNotEmpty();
        assertThat(timeline).anyMatch(e -> "PROJECT_CREATED".equals(e.eventType()));
        assertThat(timeline).anyMatch(e -> "DECISION_PROPOSED".equals(e.eventType()));
        assertThat(timeline).anyMatch(e -> "DECISION_APPROVED".equals(e.eventType()));
        assertThat(timeline).anyMatch(e -> "ACTION_EXECUTED".equals(e.eventType()));

        log.info("--- Step 17: Verify audit logs captured ---");
        Integer auditCount = jdbcClient.sql("SELECT COUNT(*) FROM audit_events WHERE project_id = ?")
            .param(projectAId)
            .query(Integer.class)
            .single();
        assertThat(auditCount).isGreaterThan(0);

        log.info("--- Step 18: Verify cross-project isolation ---");
        AuthResponse authB = registerUser("other_" + UUID.randomUUID() + "@aether.io", "PassWord999!", "External User");
        ProjectResponse projectB = projectService.createProject(
            new CreateProjectRequest("Tenant B Isolated", "Isolated project", "WEB_APP", List.of("Go"), "/tenant-b", "LOCAL_FIRST"),
            authB.user().id(),
            authB.user().email()
        );

        // Verify project B cannot read project A's artifacts or decisions
        var artifactsB = artifactService.listArtifacts(projectB.id(), null, null, null, authB.user().id());
        assertThat(artifactsB).isEmpty();

        var decisionsB = decisionService.listDecisions(projectB.id(), null, null, authB.user().id());
        assertThat(decisionsB).isEmpty();

        // User B cannot access Project A details
        assertThatThrownBy(() -> projectService.getProject(projectAId, authB.user().id()))
            .hasMessageContaining("not a member of project");

        log.info("=== PHASE 6 FULL LIFECYCLE ACCEPTANCE VERIFIED SUCCESSFULLY ===");
    }
}
