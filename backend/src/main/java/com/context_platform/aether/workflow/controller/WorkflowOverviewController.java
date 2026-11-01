package com.context_platform.aether.workflow.controller;

import com.context_platform.aether.workflow.model.ActionRequestStatus;
import com.context_platform.aether.workflow.model.FailureClassification;
import com.context_platform.aether.workflow.model.TaskStatus;
import com.context_platform.aether.workflow.repository.ActionRequestRepository;
import com.context_platform.aether.workflow.repository.TaskRepository;
import com.context_platform.aether.workflow.service.DebuggingService;
import com.context_platform.aether.workflow.service.IntegrationHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/workflow")
public class WorkflowOverviewController {

    private final IntegrationHealthService healthService;
    private final DebuggingService debuggingService;
    private final TaskRepository taskRepository;
    private final ActionRequestRepository actionRequestRepository;

    public WorkflowOverviewController(
            IntegrationHealthService healthService,
            DebuggingService debuggingService,
            TaskRepository taskRepository,
            ActionRequestRepository actionRequestRepository
    ) {
        this.healthService = healthService;
        this.debuggingService = debuggingService;
        this.taskRepository = taskRepository;
        this.actionRequestRepository = actionRequestRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<List<Map<String, Object>>> getHealth(@PathVariable UUID projectId) {
        return ResponseEntity.ok(healthService.getProjectIntegrationHealth(projectId));
    }

    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile(@PathVariable UUID projectId) {
        return ResponseEntity.ok(healthService.reconcileProject(projectId));
    }

    @GetMapping("/debugging/{failedDeploymentId}")
    public ResponseEntity<Map<String, Object>> getDebuggingPackage(
            @PathVariable UUID projectId,
            @PathVariable UUID failedDeploymentId,
            @RequestParam(defaultValue = "RUNTIME_CRASH") FailureClassification classification,
            @RequestParam(required = false) UUID taskId
    ) {
        return ResponseEntity.ok(debuggingService.assembleDebuggingPackage(projectId, taskId, failedDeploymentId, classification));
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(@PathVariable UUID projectId) {
        Map<String, Object> overview = new LinkedHashMap<>();

        Map<String, Integer> taskCounts = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            taskCounts.put(status.name(), taskRepository.countByProject(projectId, status));
        }

        int pendingApprovals = actionRequestRepository.findByProject(projectId, ActionRequestStatus.PENDING).size();
        List<Map<String, Object>> health = healthService.getProjectIntegrationHealth(projectId);

        overview.put("taskCounts", taskCounts);
        overview.put("pendingApprovalsCount", pendingApprovals);
        overview.put("integrationHealth", health);

        return ResponseEntity.ok(overview);
    }
}

