package com.context_platform.aether.auth;

public record AuthResponse(
    UserDto user,
    String token
) {}
