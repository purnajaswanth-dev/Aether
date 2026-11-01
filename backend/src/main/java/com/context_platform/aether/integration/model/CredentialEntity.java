package com.context_platform.aether.integration.model;

import java.time.Instant;
import java.util.UUID;

public record CredentialEntity(
    UUID id,
    UUID projectId,
    UUID integrationId,
    byte[] encryptedSecret,
    byte[] initializationVector,
    String keyVersion,
    byte[] authTag,
    String maskedPreview,
    Instant createdAt,
    Instant updatedAt
) {}

