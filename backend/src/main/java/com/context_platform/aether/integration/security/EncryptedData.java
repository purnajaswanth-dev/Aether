package com.context_platform.aether.integration.security;

public record EncryptedData(
    byte[] ciphertext,
    byte[] iv,
    byte[] authTag,
    String keyVersion,
    String maskedPreview
) {}

