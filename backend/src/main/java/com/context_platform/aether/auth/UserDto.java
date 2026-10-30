package com.context_platform.aether.auth;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String displayName,
    Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(user.id(), user.email(), user.displayName(), user.createdAt());
    }
}

