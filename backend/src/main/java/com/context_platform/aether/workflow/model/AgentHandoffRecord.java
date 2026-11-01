package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record AgentHandoffRecord(
        UUID id,
        UUID projectId,
        UUID taskId,
        ActorType fromActorType,
        String fromActorId,
        ActorType toActorType,
        String toActorId,
        UUID contextPackageId,
        String notes,
        Instant createdAt
) {}

