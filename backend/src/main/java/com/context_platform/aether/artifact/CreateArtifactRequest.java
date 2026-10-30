package com.context_platform.aether.artifact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateArtifactRequest(
    @NotBlank(message = "Title is required")
    @Size(min = 2, max = 255, message = "Title must be between 2 and 255 characters")
    String title,

    @NotBlank(message = "Artifact type is required")
    String artifactType,

    @NotBlank(message = "Source platform is required")
    String sourcePlatform,

    String sourceReference,

    @NotBlank(message = "Artifact content cannot be empty")
    String content,

    String changeSummary
) {}

