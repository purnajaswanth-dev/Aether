package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.ActorType;
import com.context_platform.aether.workflow.model.TaskPriority;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.model.TaskStatus;
import com.context_platform.aether.workflow.model.dto.CreateTaskRequest;
import com.context_platform.aether.workflow.model.dto.RecordTestExecutionRequest;
import com.context_platform.aether.workflow.model.dto.UpdateTaskStatusRequest;
import com.context_platform.aether.workflow.service.AgentTrackingService;
import com.context_platform.aether.workflow.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TaskStateMachineAndEvidenceTest extends BaseTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private AgentTrackingService agentTrackingService;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("state_tester@aether.test", "SecurePass123!", "State Tester");
        userId = user.user().id();
        ProjectResponse project = createTestProject("Workflow State Project", userId);
        projectId = project.id();
    }

    @Test
    void testAgentCannotDirectlySetVerifiedWithoutEvidence() {
        TaskRecord task = taskService.createTask(
                projectId,
                new CreateTaskRequest("Implement Authentication", "Add JWT auth", TaskPriority.HIGH, null, false, null),
                userId
        );

        // Move to IN_PROGRESS
        task = taskService.updateTaskStatus(projectId, task.id(), new UpdateTaskStatusRequest(
                TaskStatus.IN_PROGRESS, task.lockVersion(), "Starting work", null, ActorType.HUMAN, "user"
        ));
        assertEquals(TaskStatus.IN_PROGRESS, task.status());

        // Agent attempts to claim VERIFIED without test evidence
        TaskRecord agentResult = taskService.updateTaskStatus(projectId, task.id(), new UpdateTaskStatusRequest(
                TaskStatus.VERIFIED, task.lockVersion(), "Agent claims done", null, ActorType.AGENT, "AntigravityAgent"
        ));

        // Constitutional Rule: Coerced to DONE_UNVERIFIED
        assertEquals(TaskStatus.DONE_UNVERIFIED, agentResult.status(),
                "An agent claiming VERIFIED without evidence must be coerced to DONE_UNVERIFIED");
    }

    @Test
    void testHumanRequiresEvidenceOrOverrideForVerified() {
        TaskRecord task = taskService.createTask(
                projectId,
                new CreateTaskRequest("Payment Gateway", "Stripe integration", TaskPriority.MEDIUM, null, false, null),
                userId
        );

        task = taskService.updateTaskStatus(projectId, task.id(), new UpdateTaskStatusRequest(
                TaskStatus.IN_PROGRESS, task.lockVersion(), "Starting", null, ActorType.HUMAN, "user"
        ));
        task = taskService.updateTaskStatus(projectId, task.id(), new UpdateTaskStatusRequest(
                TaskStatus.DONE_UNVERIFIED, task.lockVersion(), "Done unverified", null, ActorType.HUMAN, "user"
        ));

        // Attempting to move to VERIFIED without passing test and without override reason must throw
        TaskRecord currentTask = task;
        assertThrows(IllegalStateException.class, () -> {
            taskService.updateTaskStatus(projectId, currentTask.id(), new UpdateTaskStatusRequest(
                    TaskStatus.VERIFIED, currentTask.lockVersion(), "Verify now", null, ActorType.HUMAN, "user"
            ));
        });

        // Human override with explicit reason succeeds
        TaskRecord verifiedWithOverride = taskService.updateTaskStatus(projectId, currentTask.id(), new UpdateTaskStatusRequest(
                TaskStatus.VERIFIED, currentTask.lockVersion(), "Audited", "Manual end-to-end verification performed in sandbox environment", ActorType.HUMAN, "user"
        ));
        assertEquals(TaskStatus.VERIFIED, verifiedWithOverride.status());
        assertEquals("Manual end-to-end verification performed in sandbox environment", verifiedWithOverride.humanOverrideReason());
    }

    @Test
    void testGenuinePassingTestExecutionEnablesVerified() {
        TaskRecord task = taskService.createTask(
                projectId,
                new CreateTaskRequest("Database Migration", "Apply V8 schema", TaskPriority.HIGH, null, false, null),
                userId
        );

        task = taskService.updateTaskStatus(projectId, task.id(), new UpdateTaskStatusRequest(
                TaskStatus.IN_PROGRESS, task.lockVersion(), "Starting work", null, ActorType.HUMAN, "user"
        ));
        task = taskService.updateTaskStatus(projectId, task.id(), new UpdateTaskStatusRequest(
                TaskStatus.DONE_UNVERIFIED, task.lockVersion(), "Candidate ready", null, ActorType.HUMAN, "user"
        ));

        // Record a genuine passing test execution
        agentTrackingService.recordTestExecution(
                projectId,
                task.id(),
                new RecordTestExecutionRequest("INTEGRATION", "JUNIT5", true, 10, 10, 0, 1500L, null, "All 10 tests passed", null, "maven-surefire")
        );

        // Fetch task: now transitioned to VERIFIED automatically
        TaskRecord verifiedTask = taskService.getTask(projectId, task.id()).orElseThrow();
        assertEquals(TaskStatus.VERIFIED, verifiedTask.status(), "Passing test execution must enable and verify the task");
    }
}
