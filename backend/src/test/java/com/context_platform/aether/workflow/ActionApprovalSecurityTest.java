package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.event.AuditEvent;
import com.context_platform.aether.event.AuditService;
import com.context_platform.aether.event.TimelineEvent;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.mcp.model.CreateMcpTokenRequest;
import com.context_platform.aether.mcp.model.CreateMcpTokenResponse;
import com.context_platform.aether.mcp.model.McpClientPlatform;
import com.context_platform.aether.mcp.service.McpTokenService;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.ActionConflictException;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.ActionRequestStatus;
import com.context_platform.aether.workflow.model.dto.ReviewActionRequest;
import com.context_platform.aether.workflow.model.dto.SubmitActionRequest;
import com.context_platform.aether.workflow.repository.ActionRequestRepository;
import com.context_platform.aether.workflow.service.ActionRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ActionApprovalSecurityTest extends BaseTest {

    @Autowired
    private ActionRequestService actionRequestService;

    @Autowired
    private ActionRequestRepository actionRequestRepository;

    @Autowired
    private McpTokenService mcpTokenService;

    @Autowired
    private TimelineService timelineService;

    @Autowired
    private AuditService auditService;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("action_sec_" + UUID.randomUUID() + "@aether.test", "SecurePass123!", "Action Security Tester");
        userId = user.user().id();
        ProjectResponse project = createTestProject("Action Security Project", userId);
        projectId = project.id();
    }

    @Test
    void testAgentTokenRequiresExecutePermission() {
        // Token without EXECUTE_APPROVED_ACTIONS
        CreateMcpTokenResponse readOnlyToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Read Only Agent", McpClientPlatform.ANTIGRAVITY, List.of("READ_PROJECT_CONTEXT", "READ_ARTIFACTS"), 60, null)
        );

        assertThrows(AccessDeniedException.class, () -> {
            actionRequestService.submitActionRequest(
                    projectId,
                    readOnlyToken.id(),
                    new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-test"), false)
            );
        }, "Token without EXECUTE_APPROVED_ACTIONS must be blocked from requesting actions");
    }

    @Test
    void testActionRemainsPendingUntilHumanApproval() {
        // Token WITH EXECUTE_APPROVED_ACTIONS
        CreateMcpTokenResponse execToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Executive Agent", McpClientPlatform.ANTIGRAVITY, List.of("READ_PROJECT_CONTEXT", "EXECUTE_APPROVED_ACTIONS"), 60, null)
        );

        // Submit action request
        ActionRequestRecord submitted = actionRequestService.submitActionRequest(
                projectId,
                execToken.id(),
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-prod"), true)
        );

        assertNotNull(submitted.id());
        assertEquals(ActionRequestStatus.PENDING, submitted.status(), "Actions must always enter PENDING status");
        assertTrue(submitted.isDestructive(), "Destructive action flag must be preserved");

        // Action is visible in approval inbox
        List<ActionRequestRecord> pending = actionRequestService.listPendingActions(projectId);
        assertTrue(pending.stream().anyMatch(a -> a.id().equals(submitted.id())));

        // Human reviewer approves action
        ActionRequestRecord approved = actionRequestService.reviewAction(
                projectId,
                submitted.id(),
                userId,
                new ReviewActionRequest(true, "Approved deployment to staging")
        );

        assertEquals(ActionRequestStatus.EXECUTED, approved.status());
        assertEquals(userId, approved.reviewedBy());
        assertNotNull(approved.reviewedAt());
        assertNotNull(approved.executionResult());
    }

    @Test
    void testHumanCanRejectAction() {
        CreateMcpTokenResponse execToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Executive Agent", McpClientPlatform.ANTIGRAVITY, List.of("EXECUTE_APPROVED_ACTIONS"), 60, null)
        );

        ActionRequestRecord submitted = actionRequestService.submitActionRequest(
                projectId,
                execToken.id(),
                new SubmitActionRequest("DROP_DATABASE", Map.of("reason", "reseed"), true)
        );

        // Human rejects action
        ActionRequestRecord rejected = actionRequestService.reviewAction(
                projectId,
                submitted.id(),
                userId,
                new ReviewActionRequest(false, "Unsafe operation on production database")
        );

        assertEquals(ActionRequestStatus.REJECTED, rejected.status());
        assertEquals("Unsafe operation on production database", rejected.reviewNotes());
    }

    @Test
    void testConcurrentApprovalRaceCondition() throws Exception {
        CreateMcpTokenResponse execToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Concurrency Agent", McpClientPlatform.ANTIGRAVITY, List.of("EXECUTE_APPROVED_ACTIONS"), 60, null)
        );

        ActionRequestRecord submitted = actionRequestService.submitActionRequest(
                projectId,
                execToken.id(),
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-race"), true)
        );

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    actionRequestService.reviewAction(
                            projectId,
                            submitted.id(),
                            userId,
                            new ReviewActionRequest(true, "Approval attempt " + index)
                    );
                    successes.incrementAndGet();
                } catch (ActionConflictException e) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    // Unexpected error
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent approvals timed out");
        executor.shutdown();

        // Exactly one thread must succeed; all others must be rejected with conflict
        assertEquals(1, successes.get(), "Exactly one approval must succeed");
        assertEquals(threadCount - 1, conflicts.get(), "All competing approvals must fail with ActionConflictException");

        ActionRequestRecord finalRecord = actionRequestRepository.findById(projectId, submitted.id()).orElseThrow();
        assertEquals(ActionRequestStatus.EXECUTED, finalRecord.status());
        assertEquals(3, finalRecord.lockVersion(), "Lock version should be 3 (1 initial + 1 claim + 1 complete)");
    }

    @Test
    void testReviewerMustBeHumanMember() {
        CreateMcpTokenResponse execToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Executive Agent", McpClientPlatform.ANTIGRAVITY, List.of("EXECUTE_APPROVED_ACTIONS"), 60, null)
        );

        ActionRequestRecord submitted = actionRequestService.submitActionRequest(
                projectId,
                execToken.id(),
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-auth"), true)
        );

        // Unauthenticated / null reviewer
        assertThrows(AccessDeniedException.class, () ->
                actionRequestService.reviewAction(projectId, submitted.id(), null, new ReviewActionRequest(true, "No user"))
        );

        // Agent token cannot review
        assertThrows(AccessDeniedException.class, () ->
                actionRequestService.reviewAction(projectId, submitted.id(), execToken.id(), new ReviewActionRequest(true, "Agent trying to approve"))
        );

        // User not a member of this project
        AuthResponse stranger = registerUser("stranger_" + UUID.randomUUID() + "@aether.test", "Pass123!", "Stranger");
        assertThrows(AccessDeniedException.class, () ->
                actionRequestService.reviewAction(projectId, submitted.id(), stranger.user().id(), new ReviewActionRequest(true, "Stranger trying to approve"))
        );
    }

    @Test
    void testCentralizedAuditAndTimelineLoggingWithMasking() {
        CreateMcpTokenResponse execToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Logging Agent", McpClientPlatform.ANTIGRAVITY, List.of("EXECUTE_APPROVED_ACTIONS"), 60, null)
        );

        Map<String, Object> sensitiveParams = Map.of(
                "serviceId", "srv-cluster-01",
                "apiKey", "super-secret-key-999",
                "db_password", "database-secret-pass",
                "auth_token", "jwt-token-val"
        );

        // 1. Submit action
        ActionRequestRecord submitted = actionRequestService.submitActionRequest(
                projectId,
                execToken.id(),
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", sensitiveParams, true)
        );

        // Verify timeline event: ACTION_REQUESTED
        List<TimelineEvent> timeline = timelineService.listTimeline(projectId, null, null, 20);
        assertTrue(timeline.stream().anyMatch(e -> e.eventType().equals("ACTION_REQUESTED")), "ACTION_REQUESTED must be logged to timeline");

        TimelineEvent reqEvent = timeline.stream().filter(e -> e.eventType().equals("ACTION_REQUESTED")).findFirst().orElseThrow();
        assertFalse(reqEvent.metadata().contains("super-secret-key-999"), "Plaintext API key must not appear in timeline metadata");
        assertFalse(reqEvent.metadata().contains("database-secret-pass"), "Plaintext password must not appear in timeline metadata");
        assertTrue(reqEvent.metadata().contains("[REDACTED]"), "Sensitive parameter values must be redacted");

        // 2. Approve action
        actionRequestService.reviewAction(
                projectId,
                submitted.id(),
                userId,
                new ReviewActionRequest(true, "Deploy confirmed")
        );

        // Verify audit events: ACTION_APPROVED and ACTION_EXECUTION_COMPLETED
        List<AuditEvent> auditEvents = auditService.listByProject(projectId, 20);
        assertTrue(auditEvents.stream().anyMatch(a -> a.action().equals("ACTION_APPROVED")), "ACTION_APPROVED must be logged to audit_events");
        assertTrue(auditEvents.stream().anyMatch(a -> a.action().equals("ACTION_EXECUTION_COMPLETED")), "ACTION_EXECUTION_COMPLETED must be logged to audit_events");

        AuditEvent approvedEvent = auditEvents.stream().filter(a -> a.action().equals("ACTION_APPROVED")).findFirst().orElseThrow();
        assertEquals(userId, approvedEvent.userId());
        assertFalse(approvedEvent.details().contains("super-secret-key-999"), "Plaintext secret must not appear in audit details");
        assertTrue(approvedEvent.details().contains("[REDACTED]"), "Audit details must have redacted sensitive parameters");

        // Verify timeline event: ACTION_EXECUTED
        List<TimelineEvent> updatedTimeline = timelineService.listTimeline(projectId, null, null, 20);
        assertTrue(updatedTimeline.stream().anyMatch(e -> e.eventType().equals("ACTION_EXECUTED")), "ACTION_EXECUTED must be logged to timeline");
    }

    @Test
    void testIdempotencyEnforced() {
        CreateMcpTokenResponse execToken = mcpTokenService.createToken(
                projectId,
                new CreateMcpTokenRequest("Idem Agent", McpClientPlatform.ANTIGRAVITY, List.of("EXECUTE_APPROVED_ACTIONS"), 60, null)
        );

        String idempotencyKey = "req-idempotency-" + UUID.randomUUID();

        ActionRequestRecord first = actionRequestService.submitActionRequest(
                projectId,
                execToken.id(),
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-1"), false, idempotencyKey)
        );

        ActionRequestRecord second = actionRequestService.submitActionRequest(
                projectId,
                execToken.id(),
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-1"), false, idempotencyKey)
        );

        assertEquals(first.id(), second.id(), "Submissions with the same idempotency key must return the identical action request");
        assertEquals(1, actionRequestRepository.findByProject(projectId, ActionRequestStatus.PENDING).stream()
                .filter(a -> idempotencyKey.equals(a.idempotencyKey())).count());
    }
}
