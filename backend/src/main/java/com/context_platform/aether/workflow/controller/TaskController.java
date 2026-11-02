package com.context_platform.aether.workflow.controller;

import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.security.UserPrincipal;
import com.context_platform.aether.workflow.model.AgentHandoffRecord;
import com.context_platform.aether.workflow.model.TaskEventRecord;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.model.TaskStatus;
import com.context_platform.aether.workflow.model.dto.*;
import com.context_platform.aether.workflow.repository.AgentHandoffRepository;
import com.context_platform.aether.workflow.repository.TaskEventRepository;
import com.context_platform.aether.workflow.service.AgentTrackingService;
import com.context_platform.aether.workflow.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/projects/{projectId}/tasks", "/api/projects/{projectId}/tasks"})
public class TaskController {

    private final TaskService taskService;
    private final AgentTrackingService agentTrackingService;
    private final TaskEventRepository taskEventRepository;
    private final AgentHandoffRepository handoffRepository;
    private final ProjectSecurityChecker securityChecker;

    public TaskController(
            TaskService taskService,
            AgentTrackingService agentTrackingService,
            TaskEventRepository taskEventRepository,
            AgentHandoffRepository handoffRepository,
            ProjectSecurityChecker securityChecker
    ) {
        this.taskService = taskService;
        this.agentTrackingService = agentTrackingService;
        this.taskEventRepository = taskEventRepository;
        this.handoffRepository = handoffRepository;
        this.securityChecker = securityChecker;
    }

    @PostMapping
    public ResponseEntity<TaskRecord> createTask(
            @PathVariable UUID projectId,
            @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "MEMBER");
        }
        UUID createdBy = principal != null ? principal.id() : null;
        return ResponseEntity.ok(taskService.createTask(projectId, req, createdBy));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<TaskRecord>> listTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "VIEWER");
        }
        return ResponseEntity.ok(taskService.listTasks(projectId, status, limit, offset));
    }

    @GetMapping("/{taskId}")
    @Transactional(readOnly = true)
    public ResponseEntity<TaskRecord> getTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "VIEWER");
        }
        return taskService.getTask(projectId, taskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/status")
    public ResponseEntity<TaskRecord> updateStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestBody UpdateTaskStatusRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "MEMBER");
        }
        return ResponseEntity.ok(taskService.updateTaskStatus(projectId, taskId, req));
    }

    @PostMapping("/{taskId}/accept")
    public ResponseEntity<TaskRecord> acceptProposal(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "MEMBER");
        }
        UUID userId = principal != null ? principal.id() : UUID.randomUUID();
        return ResponseEntity.ok(taskService.acceptTaskProposal(projectId, taskId, userId));
    }

    @GetMapping("/{taskId}/context")
    @Transactional(readOnly = true)
    public ResponseEntity<TaskContextResponse> getTaskContext(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "VIEWER");
        }
        return ResponseEntity.ok(taskService.getTaskContext(projectId, taskId));
    }

    @PostMapping("/{taskId}/implement")
    public ResponseEntity<?> recordImplementation(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestBody RecordImplementationRequest req
    ) {
        return ResponseEntity.ok(agentTrackingService.recordImplementation(projectId, taskId, req));
    }

    @PostMapping("/{taskId}/tests")
    public ResponseEntity<?> recordTestExecution(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestBody RecordTestExecutionRequest req
    ) {
        return ResponseEntity.ok(agentTrackingService.recordTestExecution(projectId, taskId, req));
    }

    @GetMapping("/{taskId}/events")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TaskEventRecord>> getTaskEvents(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "VIEWER");
        }
        return ResponseEntity.ok(taskEventRepository.findByTaskId(projectId, taskId));
    }

    @GetMapping("/{taskId}/handoffs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AgentHandoffRecord>> getTaskHandoffs(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "VIEWER");
        }
        return ResponseEntity.ok(handoffRepository.findByTaskId(projectId, taskId));
    }
}
