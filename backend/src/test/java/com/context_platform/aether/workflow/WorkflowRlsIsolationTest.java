package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.TaskPriority;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.model.dto.CreateTaskRequest;
import com.context_platform.aether.workflow.repository.TaskRepository;
import com.context_platform.aether.workflow.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowRlsIsolationTest extends BaseTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void testTaskProjectIsolation() {
        AuthResponse userA = registerUser("user_a@aether.test", "SecurePass123!", "User Alpha");
        UUID userId = userA.user().id();

        // Project A
        ProjectResponse projA = createTestProject("Project Alpha", userId);

        // Project B
        ProjectResponse projB = createTestProject("Project Beta", userId);

        TaskRecord taskA = taskService.createTask(
                projA.id(),
                new CreateTaskRequest("Secret Alpha Task", "Alpha secret implementation", TaskPriority.HIGH, userId, false, null),
                userId
        );

        // Project A can find taskA
        assertTrue(taskRepository.findById(projA.id(), taskA.id()).isPresent());

        // Project B CANNOT find taskA
        assertTrue(taskRepository.findById(projB.id(), taskA.id()).isEmpty(),
                "Project B must never see tasks belonging to Project A");

        // Verify task counts are isolated
        assertEquals(1, taskRepository.countByProject(projA.id(), null));
        assertEquals(0, taskRepository.countByProject(projB.id(), null));
    }
}
