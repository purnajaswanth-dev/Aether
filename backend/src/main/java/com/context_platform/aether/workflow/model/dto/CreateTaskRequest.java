package com.context_platform.aether.workflow.model.dto;

import com.context_platform.aether.workflow.model.TaskPriority;
import java.util.UUID;

public record CreateTaskRequest(
        String title,
        String description,
        TaskPriority priority,
        UUID assigneeId,
        boolean proposedByAgent,
        String agentProposalReason
) {}

