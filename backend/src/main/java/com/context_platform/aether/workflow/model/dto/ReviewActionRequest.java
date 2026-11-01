package com.context_platform.aether.workflow.model.dto;

public record ReviewActionRequest(
        boolean approve,
        String notes
) {}

