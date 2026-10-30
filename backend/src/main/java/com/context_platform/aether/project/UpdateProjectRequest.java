package com.context_platform.aether.project;

import java.util.List;

public record UpdateProjectRequest(
    String name,
    String description,
    String projectType,
    List<String> techStack,
    String directory,
    String storagePreference
) {}

