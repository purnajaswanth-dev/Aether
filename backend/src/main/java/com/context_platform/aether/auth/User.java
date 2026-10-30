package com.context_platform.aether.auth;

import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String email,
    String passwordHash,
    String displayName,
    Instant createdAt,
    Instant updatedAt
) {}
