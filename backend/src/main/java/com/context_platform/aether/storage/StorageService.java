package com.context_platform.aether.storage;

import java.util.UUID;

public interface StorageService {
    StorageResult store(UUID projectId, String filename, byte[] content);
    byte[] retrieve(UUID projectId, String storageKey, String expectedHash);
    void verifyIntegrity(UUID projectId, String storageKey, String expectedHash);
    String computeSha256(byte[] content);
}
