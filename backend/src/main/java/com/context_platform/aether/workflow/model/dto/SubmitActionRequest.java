package com.context_platform.aether.workflow.model.dto;

import java.util.Map;

public record SubmitActionRequest(
        String actionType,
        Map<String, Object> parameters,
        boolean isDestructive,
        String idempotencyKey
) {
    public SubmitActionRequest(String actionType, Map<String, Object> parameters, boolean isDestructive) {
        this(actionType, parameters, isDestructive, null);
    }
}
