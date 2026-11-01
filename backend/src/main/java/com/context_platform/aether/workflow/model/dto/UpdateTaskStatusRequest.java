package com.context_platform.aether.workflow.model.dto;

import com.context_platform.aether.workflow.model.ActorType;
import com.context_platform.aether.workflow.model.TaskStatus;

public record UpdateTaskStatusRequest(
        TaskStatus newStatus,
        int expectedLockVersion,
        String reason,
        String humanOverrideReason,
        ActorType actorType,
        String actorId
) {}

