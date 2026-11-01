package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record DebuggingRecord(
        UUID id,
        UUID projectId,
        UUID taskId,
        String failureType,
        FailureClassification classification,
        UUID failedDeploymentId,
        UUID resolvedDeploymentId,
        String rootCauseAnalysis,
        String attemptSummary,
        DebuggingStatus status,
        Instant createdAt,
        Instant resolvedAt
) {}

