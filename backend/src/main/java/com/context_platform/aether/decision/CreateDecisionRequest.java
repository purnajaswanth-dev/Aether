package com.context_platform.aether.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDecisionRequest(
    @NotBlank(message = "Decision title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    String title,

    @NotBlank(message = "Category is required")
    String category,

    @NotBlank(message = "Problem statement is required")
    String problemStatement,

    @NotBlank(message = "Proposed solution is required")
    String proposedSolution,

    String rationale,

    String consequences,

    UUID sourceArtifactVersionId
) {}

