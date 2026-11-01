package com.context_platform.aether.workflow.model.dto;

import java.util.List;
import java.util.Map;

public record ChangeImpactResult(
        List<String> targetFiles,
        List<String> affectedModules,
        List<String> affectedApis,
        List<String> affectedTests,
        List<String> affectedTasks,
        Map<String, String> confidenceLevels,
        List<String> reasoningEdges
) {}

