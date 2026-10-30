package com.context_platform.aether.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProjectRequest(
    @NotBlank(message = "Project name is required")
    @Size(min = 2, max = 255, message = "Project name must be between 2 and 255 characters")
    String name,

    String description,

    @NotBlank(message = "Project type is required")
    String projectType,

    List<String> techStack,

    String directory,

    @Pattern(regexp = "LOCAL_FIRST|CLOUD|HYBRID", message = "Storage preference must be LOCAL_FIRST, CLOUD, or HYBRID")
    String storagePreference
) {}
