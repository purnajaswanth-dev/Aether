package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.ActorType;
import com.context_platform.aether.workflow.model.TaskPriority;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.model.TaskStatus;
import com.context_platform.aether.workflow.model.dto.CreateTaskRequest;
import com.context_platform.aether.workflow.model.dto.UpdateTaskStatusRequest;
import com.context_platform.aether.workflow.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentTaskUpdateTest extends BaseTest {

    @Autowired
    private TaskService taskService;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("concurrent_tester@aether.test", "SecurePass123!", "Concurrent Tester");
        userId = user.user().id();
        ProjectResponse project = createTestProject("Concurrent Test Project", userId);
        projectId = project.id();
    }

    @Test
    void testOptimisticLockingPreventsLostUpdates() {
        TaskRecord task = taskService.createTask(
                projectId,
                new CreateTaskRequest("Concurrent Task", "Inspect concurrency", TaskPriority.HIGH, null, false, null),
                userId
        );
        assertEquals(1, task.lockVersion());

        // First user updates task
        TaskRecord updated1 = taskService.updateTaskStatus(
                projectId,
                task.id(),
                new UpdateTaskStatusRequest(TaskStatus.READY, 1, "User 1 ready", null, ActorType.HUMAN, "user-1")
        );
        assertEquals(2, updated1.lockVersion());

        // Second user attempts update using stale lock_version=1
        assertThrows(OptimisticLockingFailureException.class, () -> {
            taskService.updateTaskStatus(
                    projectId,
                    task.id(),
                    new UpdateTaskStatusRequest(TaskStatus.CANCELLED, 1, "User 2 cancel", null, ActorType.HUMAN, "user-2")
            );
        });

        // Updating with current lock_version=2 succeeds
        TaskRecord updated2 = taskService.updateTaskStatus(
                projectId,
                task.id(),
                new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS, 2, "User 2 in progress", null, ActorType.HUMAN, "user-2")
        );
        assertEquals(3, updated2.lockVersion());
        assertEquals(TaskStatus.IN_PROGRESS, updated2.status());
    }
}
