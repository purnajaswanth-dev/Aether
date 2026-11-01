package com.context_platform.aether.workflow.service;

import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.decision.DecisionRepository;
import com.context_platform.aether.decision.TechnicalDecision;
import com.context_platform.aether.workflow.model.*;
import com.context_platform.aether.workflow.model.dto.CreateTaskRequest;
import com.context_platform.aether.workflow.model.dto.TaskContextResponse;
import com.context_platform.aether.workflow.model.dto.UpdateTaskStatusRequest;
import com.context_platform.aether.workflow.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskStateMachine taskStateMachine;
    private final TaskLinkRepository taskLinkRepository;
    private final ImplementationRecordRepository implementationRecordRepository;
    private final DebuggingRecordRepository debuggingRecordRepository;
    private final DecisionRepository decisionRepository;
    private final ArtifactRepository artifactRepository;

    public TaskService(
            TaskRepository taskRepository,
            TaskEventRepository taskEventRepository,
            TaskStateMachine taskStateMachine,
            TaskLinkRepository taskLinkRepository,
            ImplementationRecordRepository implementationRecordRepository,
            DebuggingRecordRepository debuggingRecordRepository,
            DecisionRepository decisionRepository,
            ArtifactRepository artifactRepository
    ) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.taskStateMachine = taskStateMachine;
        this.taskLinkRepository = taskLinkRepository;
        this.implementationRecordRepository = implementationRecordRepository;
        this.debuggingRecordRepository = debuggingRecordRepository;
        this.decisionRepository = decisionRepository;
        this.artifactRepository = artifactRepository;
    }

    @Transactional
    public TaskRecord createTask(UUID projectId, CreateTaskRequest req, UUID createdBy) {
        TaskRecord task = new TaskRecord(
                null,
                projectId,
                0,
                req.title(),
                req.description(),
                req.proposedByAgent() ? TaskStatus.BACKLOG : TaskStatus.READY,
                req.priority() != null ? req.priority() : TaskPriority.MEDIUM,
                req.assigneeId(),
                req.proposedByAgent(),
                req.agentProposalReason(),
                null,
                1,
                createdBy,
                null,
                null
        );

        TaskRecord saved = taskRepository.create(task);

        taskEventRepository.save(new TaskEventRecord(
                null,
                projectId,
                saved.id(),
                TaskEventType.STATUS_CHANGE,
                null,
                saved.status().name(),
                req.proposedByAgent() ? ActorType.AGENT : ActorType.HUMAN,
                createdBy != null ? createdBy.toString() : "system",
                req.proposedByAgent() ? "Proposed by AI agent: " + req.agentProposalReason() : "Created task",
                Map.of(),
                null
        ));

        return saved;
    }

    @Transactional
    public TaskRecord acceptTaskProposal(UUID projectId, UUID taskId, UUID userId) {
        TaskRecord task = taskRepository.findById(projectId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!task.proposedByAgent() || task.status() != TaskStatus.BACKLOG) {
            throw new IllegalStateException("Task is not an unaccepted agent proposal");
        }

        TaskRecord updated = new TaskRecord(
                task.id(),
                task.projectId(),
                task.taskNumber(),
                task.title(),
                task.description(),
                TaskStatus.READY,
                task.priority(),
                task.assigneeId(),
                false, // Proposal accepted by human
                task.agentProposalReason(),
                null,
                task.lockVersion(),
                task.createdBy(),
                task.createdAt(),
                null
        );

        TaskRecord saved = taskRepository.update(updated, task.lockVersion());

        taskEventRepository.save(new TaskEventRecord(
                null,
                projectId,
                taskId,
                TaskEventType.PROPOSAL_ACCEPTED,
                TaskStatus.BACKLOG.name(),
                TaskStatus.READY.name(),
                ActorType.HUMAN,
                userId.toString(),
                "Human accepted agent proposed task",
                Map.of(),
                null
        ));

        return saved;
    }

    @Transactional
    public TaskRecord updateTaskStatus(UUID projectId, UUID taskId, UpdateTaskStatusRequest req) {
        TaskRecord task = taskRepository.findById(projectId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        ActorType actorType = req.actorType() != null ? req.actorType() : ActorType.HUMAN;

        // Evaluate target status through the strict state machine
        TaskStatus finalStatus = taskStateMachine.evaluateTargetStatus(
                task,
                req.newStatus(),
                actorType,
                req.humanOverrideReason()
        );

        TaskRecord updated = new TaskRecord(
                task.id(),
                task.projectId(),
                task.taskNumber(),
                task.title(),
                task.description(),
                finalStatus,
                task.priority(),
                task.assigneeId(),
                task.proposedByAgent(),
                task.agentProposalReason(),
                req.humanOverrideReason() != null ? req.humanOverrideReason() : task.humanOverrideReason(),
                task.lockVersion(),
                task.createdBy(),
                task.createdAt(),
                null
        );

        TaskRecord saved = taskRepository.update(updated, req.expectedLockVersion());

        taskEventRepository.save(new TaskEventRecord(
                null,
                projectId,
                taskId,
                TaskEventType.STATUS_CHANGE,
                task.status().name(),
                finalStatus.name(),
                actorType,
                req.actorId() != null ? req.actorId() : "system",
                req.reason() != null ? req.reason() : (actorType == ActorType.AGENT && finalStatus == TaskStatus.DONE_UNVERIFIED ? "Implementation recorded by agent (awaiting test verification)" : "Status updated"),
                req.humanOverrideReason() != null ? Map.of("human_override", req.humanOverrideReason()) : Map.of(),
                null
        ));

        return saved;
    }

    @Transactional
    public void linkTarget(UUID projectId, UUID taskId, TaskLinkType linkType, String targetId, String targetLabel) {
        taskLinkRepository.save(new TaskLinkRecord(
                null, projectId, taskId, linkType, targetId, targetLabel, null
        ));

        taskEventRepository.save(new TaskEventRecord(
                null,
                projectId,
                taskId,
                TaskEventType.EVIDENCE_LINKED,
                null,
                null,
                ActorType.SYSTEM,
                "system",
                "Linked " + linkType + ": " + targetLabel,
                Map.of("targetId", targetId, "linkType", linkType.name()),
                null
        ));
    }

    public Optional<TaskRecord> getTask(UUID projectId, UUID taskId) {
        return taskRepository.findById(projectId, taskId);
    }

    public List<TaskRecord> listTasks(UUID projectId, TaskStatus status, int limit, int offset) {
        return taskRepository.findByProject(projectId, status, limit, offset);
    }

    public TaskContextResponse getTaskContext(UUID projectId, UUID taskId) {
        TaskRecord task = taskRepository.findById(projectId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        List<TaskLinkRecord> links = taskLinkRepository.findByTaskId(projectId, taskId);

        List<Map<String, Object>> decisions = new ArrayList<>();
        List<Map<String, Object>> artifacts = new ArrayList<>();

        for (TaskLinkRecord link : links) {
            if (link.linkType() == TaskLinkType.DECISION) {
                try {
                    UUID decId = UUID.fromString(link.targetId());
                    decisionRepository.findById(decId, projectId).ifPresent(d -> {
                        decisions.add(Map.of(
                                "id", d.id(),
                                "title", d.title(),
                                "status", d.state(),
                                "category", d.category()
                        ));
                    });
                } catch (Exception ignored) {}
            } else if (link.linkType() == TaskLinkType.ARTIFACT) {
                try {
                    UUID artId = UUID.fromString(link.targetId());
                    artifactRepository.findArtifactById(artId, projectId).ifPresent(a -> {
                        artifacts.add(Map.of(
                                "id", a.id(),
                                "title", a.title(),
                                "type", a.artifactType(),
                                "sourcePlatform", a.sourcePlatform() != null ? a.sourcePlatform() : "AETHER"
                        ));
                    });
                } catch (Exception ignored) {}
            }
        }

        ImplementationRecord latestImpl = implementationRecordRepository.findLatestByTaskId(projectId, taskId).orElse(null);
        List<DebuggingRecord> debuggingAttempts = debuggingRecordRepository.findByTaskId(projectId, taskId);

        List<String> openIssues = latestImpl != null ? latestImpl.unresolvedIssues() : List.of();

        return new TaskContextResponse(
                task,
                decisions,
                artifacts,
                latestImpl,
                debuggingAttempts,
                openIssues
        );
    }
}
