package com.context_platform.aether.workflow.service;

import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.workflow.model.*;
import com.context_platform.aether.workflow.model.dto.RecordImplementationRequest;
import com.context_platform.aether.workflow.model.dto.RecordTestExecutionRequest;
import com.context_platform.aether.workflow.model.dto.UpdateTaskStatusRequest;
import com.context_platform.aether.workflow.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentTrackingService {

    private final AgentSessionRepository sessionRepository;
    private final AgentInteractionRepository interactionRepository;
    private final AgentHandoffRepository handoffRepository;
    private final ImplementationRecordRepository implementationRepository;
    private final TestExecutionRepository testExecutionRepository;
    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final ArtifactService artifactService;

    public AgentTrackingService(
            AgentSessionRepository sessionRepository,
            AgentInteractionRepository interactionRepository,
            AgentHandoffRepository handoffRepository,
            ImplementationRecordRepository implementationRepository,
            TestExecutionRepository testExecutionRepository,
            TaskService taskService,
            TaskRepository taskRepository,
            ArtifactService artifactService
    ) {
        this.sessionRepository = sessionRepository;
        this.interactionRepository = interactionRepository;
        this.handoffRepository = handoffRepository;
        this.implementationRepository = implementationRepository;
        this.testExecutionRepository = testExecutionRepository;
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.artifactService = artifactService;
    }

    public AgentSessionRecord startSession(UUID projectId, String clientPlatform, UUID taskId) {
        AgentSessionRecord session = new AgentSessionRecord(
                null,
                projectId,
                clientPlatform,
                taskId,
                Instant.now(),
                null,
                "ACTIVE",
                null,
                null
        );
        return sessionRepository.save(session);
    }

    public void recordInteraction(UUID projectId, UUID sessionId, UUID taskId, String type, String toolName, UUID artifactId, Map<String, Object> details) {
        interactionRepository.save(new AgentInteractionRecord(
                null,
                projectId,
                sessionId,
                taskId,
                type,
                toolName,
                artifactId,
                details != null ? details : Map.of(),
                null
        ));
    }

    public AgentHandoffRecord recordHandoff(
            UUID projectId,
            UUID taskId,
            ActorType fromType,
            String fromId,
            ActorType toType,
            String toId,
            UUID contextPackageId,
            String notes
    ) {
        return handoffRepository.save(new AgentHandoffRecord(
                null,
                projectId,
                taskId,
                fromType,
                fromId,
                toType,
                toId,
                contextPackageId,
                notes,
                null
        ));
    }

    @Transactional
    public ImplementationRecord recordImplementation(UUID projectId, UUID taskId, RecordImplementationRequest req) {
        TaskRecord task = taskRepository.findById(projectId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        ImplementationRecord record = new ImplementationRecord(
                null,
                projectId,
                taskId,
                req.sessionId(),
                req.agentPlatform() != null ? req.agentPlatform() : "AI_AGENT",
                req.summary(),
                req.modifiedFiles() != null ? req.modifiedFiles() : List.of(),
                req.unresolvedIssues() != null ? req.unresolvedIssues() : List.of(),
                "DONE_UNVERIFIED",
                null
        );

        ImplementationRecord saved = implementationRepository.save(record);

        // Move task to DONE_UNVERIFIED
        taskService.updateTaskStatus(projectId, taskId, new UpdateTaskStatusRequest(
                TaskStatus.DONE_UNVERIFIED,
                task.lockVersion(),
                "Implementation recorded by " + record.agentPlatform() + ": " + req.summary(),
                null,
                ActorType.AGENT,
                req.agentPlatform()
        ));

        // If session exists, record interaction
        if (req.sessionId() != null) {
            recordInteraction(projectId, req.sessionId(), taskId, "IMPLEMENTATION_RECORDED", "record_implementation_result", null, Map.of(
                    "modifiedFilesCount", record.modifiedFiles().size(),
                    "unresolvedIssuesCount", record.unresolvedIssues().size()
            ));
        }

        return saved;
    }

    @Transactional
    public TestExecutionRecord recordTestExecution(UUID projectId, UUID taskId, RecordTestExecutionRequest req) {
        TaskRecord task = taskRepository.findById(projectId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        UUID logArtifactId = null;
        if (req.rawLogs() != null && !req.rawLogs().isBlank()) {
            try {
                ActorContext logActor = req.sessionId() != null
                        ? ActorContext.agent(req.sessionId().toString())
                        : ActorContext.system();
                // Save original test execution output as an immutable artifact with hash
                var created = artifactService.createArtifact(
                        projectId,
                        new CreateArtifactRequest(
                                "Test Execution Logs - " + task.key() + " (" + req.framework() + ")",
                                "LOGS",
                                req.framework() != null ? req.framework() : "TEST_RUNNER",
                                "test_evidence_" + System.currentTimeMillis() + ".log",
                                req.rawLogs(),
                                "Raw test suite execution output recorded as verification evidence."
                        ),
                        logActor
                );
                logArtifactId = created.id();
            } catch (Exception ignored) {}
        }

        TestExecutionRecord record = new TestExecutionRecord(
                null,
                projectId,
                taskId,
                req.sessionId(),
                req.testType() != null ? req.testType() : "UNIT",
                req.framework() != null ? req.framework() : "UNKNOWN",
                req.passed(),
                req.totalTests(),
                req.passedTests(),
                req.failedTests(),
                req.durationMs(),
                req.errorSummary(),
                logArtifactId,
                req.recordedBy() != null ? req.recordedBy() : "TEST_RUNNER",
                null
        );

        TestExecutionRecord saved = testExecutionRepository.save(record);

        if (logArtifactId != null) {
            taskService.linkTarget(projectId, taskId, TaskLinkType.ARTIFACT, logArtifactId.toString(), "Test Execution Logs (" + req.framework() + ")");
        }

        // If test passed and task is currently DONE_UNVERIFIED or IN_REVIEW, transition to VERIFIED
        if (req.passed() && (task.status() == TaskStatus.DONE_UNVERIFIED || task.status() == TaskStatus.IN_REVIEW)) {
            taskService.updateTaskStatus(projectId, taskId, new UpdateTaskStatusRequest(
                    TaskStatus.VERIFIED,
                    task.lockVersion(),
                    "Verified by passing " + req.framework() + " test suite (" + req.passedTests() + "/" + req.totalTests() + " passed)",
                    null,
                    ActorType.SYSTEM,
                    "test_runner"
            ));
        }

        return saved;
    }
}
