package com.context_platform.aether.artifact;

import jakarta.validation.constraints.NotBlank;

public record CreateVersionRequest(
    @NotBlank(message = "Artifact content cannot be empty")
    String content,

    String changeSummary
) {}

