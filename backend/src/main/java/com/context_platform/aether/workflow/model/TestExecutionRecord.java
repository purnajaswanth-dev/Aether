package com.context_platform.aether.workflow.model;

import java.time.Instant;
import java.util.UUID;

public record TestExecutionRecord(
        UUID id,
        UUID projectId,
        UUID taskId,
        UUID sessionId,
        String testType,
        String framework,
        boolean passed,
        int totalTests,
        int passedTests,
        int failedTests,
        long durationMs,
        String errorSummary,
        UUID logArtifactId,
        String recordedBy,
        Instant createdAt
) {}

