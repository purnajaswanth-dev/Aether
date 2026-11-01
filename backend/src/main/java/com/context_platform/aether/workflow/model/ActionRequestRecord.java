package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActionRequestRecord(
        UUID id,
        UUID projectId,
        UUID agentTokenId,
        String actionType,
        Map<String, Object> parameters,
        boolean isDestructive,
        ActionRequestStatus status,
        Instant requestedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNotes,
        Map<String, Object> executionResult,
        int lockVersion,
        String idempotencyKey,
        Instant claimedAt,
        Instant executedAt
) {
    public ActionRequestRecord(
            UUID id,
            UUID projectId,
            UUID agentTokenId,
            String actionType,
            Map<String, Object> parameters,
            boolean isDestructive,
            ActionRequestStatus status,
            Instant requestedAt,
            UUID reviewedBy,
            Instant reviewedAt,
            String reviewNotes,
            Map<String, Object> executionResult
    ) {
        this(id, projectId, agentTokenId, actionType, parameters, isDestructive, status, requestedAt, reviewedBy, reviewedAt, reviewNotes, executionResult, 1, null, null, null);
    }
}
