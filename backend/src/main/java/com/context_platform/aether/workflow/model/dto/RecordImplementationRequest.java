package com.context_platform.aether.workflow.model.dto;

import java.util.List;
import java.util.UUID;

public record RecordImplementationRequest(
        String summary,
        List<String> modifiedFiles,
        List<String> unresolvedIssues,
        UUID sessionId,
        String agentPlatform
) {}

