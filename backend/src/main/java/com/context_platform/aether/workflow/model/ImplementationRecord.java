package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImplementationRecord(
        UUID id,
        UUID projectId,
        UUID taskId,
        UUID sessionId,
        String agentPlatform,
        String summary,
        List<String> modifiedFiles,
        List<String> unresolvedIssues,
        String candidateStatus,
        Instant createdAt
) {}

