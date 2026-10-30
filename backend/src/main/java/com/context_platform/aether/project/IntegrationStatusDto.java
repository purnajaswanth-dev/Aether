package com.context_platform.aether.project;

import java.util.List;
import java.util.UUID;

public record IntegrationStatusDto(
    UUID id,
    String providerId,
    String name,
    String category,
    String description,
    String status, // NOT_CONNECTED, NOT_AVAILABLE
    List<String> plannedCapabilities,
    String statusReason
) {}
