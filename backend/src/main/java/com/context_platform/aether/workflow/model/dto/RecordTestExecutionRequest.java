package com.context_platform.aether.workflow.model.dto;

import java.util.UUID;

public record RecordTestExecutionRequest(
        String testType,
        String framework,
        boolean passed,
        int totalTests,
        int passedTests,
        int failedTests,
        long durationMs,
        String errorSummary,
        String rawLogs,
        UUID sessionId,
        String recordedBy
) {}

