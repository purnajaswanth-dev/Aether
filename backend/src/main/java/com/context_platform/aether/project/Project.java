package com.context_platform.aether.project;

import java.time.Instant;
import java.util.UUID;

public record Project(
    UUID id,
    String publicId,
    String name,
    String description,
    String projectType,
    String techStack, // JSON array string
    String directory,
    String storagePreference,
    boolean archived,
    Instant createdAt,
    Instant updatedAt
) {}
