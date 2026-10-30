package com.context_platform.aether.storage;

public record StorageResult(
    String storageKey,
    String contentHash,
    long byteSize
) {}

