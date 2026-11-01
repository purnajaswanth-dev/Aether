package com.context_platform.aether.workflow.service;

import com.context_platform.aether.workflow.model.ActorType;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.model.TaskStatus;
import com.context_platform.aether.workflow.repository.TaskLinkRepository;
import com.context_platform.aether.workflow.repository.TestExecutionRepository;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TaskStateMachine {

    private final TestExecutionRepository testExecutionRepository;
    private final TaskLinkRepository taskLinkRepository;

    public TaskStateMachine(TestExecutionRepository testExecutionRepository, TaskLinkRepository taskLinkRepository) {
        this.testExecutionRepository = testExecutionRepository;
        this.taskLinkRepository = taskLinkRepository;
    }

    public boolean isValidTransition(TaskStatus current, TaskStatus target) {
        if (current == target) return true;
        if (target == TaskStatus.CANCELLED) return true;

        return switch (current) {
            case BACKLOG -> target == TaskStatus.READY;
            case READY -> target == TaskStatus.IN_PROGRESS;
            case IN_PROGRESS -> target == TaskStatus.IN_REVIEW
                    || target == TaskStatus.BLOCKED
                    || target == TaskStatus.DONE_UNVERIFIED
                    || target == TaskStatus.VERIFIED;
            case BLOCKED -> target == TaskStatus.READY || target == TaskStatus.IN_PROGRESS;
            case IN_REVIEW -> target == TaskStatus.IN_PROGRESS
                    || target == TaskStatus.READY
                    || target == TaskStatus.DONE_UNVERIFIED
                    || target == TaskStatus.VERIFIED;
            case DONE_UNVERIFIED -> target == TaskStatus.VERIFIED
                    || target == TaskStatus.IN_PROGRESS
                    || target == TaskStatus.IN_REVIEW;
            case VERIFIED -> target == TaskStatus.IN_PROGRESS; // Re-opened if bug found
            case CANCELLED -> target == TaskStatus.BACKLOG;
        };
    }

    /**
     * Enforces the constitutional rule:
     * An agent may move a task to DONE_UNVERIFIED by reporting implementation.
     * Only recorded evidence can enable VERIFIED: a passing test execution linked to the task,
     * or an explicit human override with a non-blank reason.
     */
    public TaskStatus evaluateTargetStatus(
            TaskRecord task,
            TaskStatus requestedStatus,
            ActorType actorType,
            String humanOverrideReason
    ) {
        if (!isValidTransition(task.status(), requestedStatus)) {
            throw new IllegalStateException("Invalid task transition from " + task.status() + " to " + requestedStatus);
        }

        // Rule 1: An agent can NEVER transition directly to VERIFIED on its claim alone
        if (requestedStatus == TaskStatus.VERIFIED && actorType == ActorType.AGENT) {
            // Check if evidence exists anyway
            boolean hasPassingTests = testExecutionRepository.hasPassingTestForTask(task.projectId(), task.id());
            if (!hasPassingTests) {
                // Coerce to DONE_UNVERIFIED as mandated by constitutional rule
                return TaskStatus.DONE_UNVERIFIED;
            }
        }

        // Rule 2: Moving to VERIFIED requires evidence or human override
        if (requestedStatus == TaskStatus.VERIFIED) {
            boolean hasHumanOverride = humanOverrideReason != null && !humanOverrideReason.trim().isBlank();
            boolean hasPassingTests = testExecutionRepository.hasPassingTestForTask(task.projectId(), task.id());

            if (!hasPassingTests && !hasHumanOverride) {
                throw new IllegalStateException(
                        "Task " + task.key() + " cannot be transitioned to VERIFIED without passing test execution evidence or an explicit human override reason."
                );
            }
        }

        return requestedStatus;
    }
}
