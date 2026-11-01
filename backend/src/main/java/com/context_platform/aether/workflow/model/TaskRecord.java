package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record TaskRecord(
        UUID id,
        UUID projectId,
        int taskNumber,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        UUID assigneeId,
        boolean proposedByAgent,
        String agentProposalReason,
        String humanOverrideReason,
        int lockVersion,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public String key() {
        return "TASK-" + taskNumber;
    }
}

