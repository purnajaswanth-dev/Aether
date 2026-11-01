package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.UUID;

public record AgentDeviceRecord(
    UUID id,
    UUID projectId,
    String deviceCode,
    String userCode,
    String deviceTokenHash,
    String hostname,
    String os,
    String localPath,
    String status,
    Instant expiresAt,
    Instant lastHeartbeatAt,
    Instant createdAt
) {}

