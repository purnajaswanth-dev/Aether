package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeploymentRecord(
    UUID id,
    UUID projectId,
    ProviderType provider,
    String serviceId,
    String externalDeployId,
    String environment,
    String commitSha,
    String status,
    String failureClassification,
    UUID buildLogsArtifactId,
    UUID runtimeLogsArtifactId,
    List<String> extractedErrors,
    UUID linkedTaskId,
    Instant deployStartedAt,
    Instant deployFinishedAt,
    Instant createdAt
) {}

