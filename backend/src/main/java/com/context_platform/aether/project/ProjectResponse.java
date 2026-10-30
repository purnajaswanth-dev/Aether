package com.context_platform.aether.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String publicId,
    String name,
    String description,
    String projectType,
    List<String> techStack,
    String directory,
    String storagePreference,
    boolean archived,
    String currentUserRole,
    Instant createdAt,
    Instant updatedAt
) {}
