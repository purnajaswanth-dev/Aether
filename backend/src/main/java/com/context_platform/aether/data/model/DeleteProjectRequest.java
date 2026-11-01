package com.context_platform.aether.data.model;

import jakarta.validation.constraints.NotBlank;

public record DeleteProjectRequest(
    @NotBlank(message = "Confirmation code is required")
    String confirmationCode
) {}

