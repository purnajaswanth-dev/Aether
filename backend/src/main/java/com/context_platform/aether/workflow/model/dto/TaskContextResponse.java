package com.context_platform.aether.workflow.model.dto;

import com.context_platform.aether.workflow.model.DebuggingRecord;
import com.context_platform.aether.workflow.model.ImplementationRecord;
import com.context_platform.aether.workflow.model.TaskRecord;
import java.util.List;
import java.util.Map;

public record TaskContextResponse(
        TaskRecord task,
        List<Map<String, Object>> linkedDecisions,
        List<Map<String, Object>> relevantArtifacts,
        ImplementationRecord latestImplementation,
        List<DebuggingRecord> priorAttempts,
        List<String> openIssues
) {}

