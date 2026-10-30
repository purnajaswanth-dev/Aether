package com.context_platform.aether.storage;

import com.context_platform.aether.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private final Path rootPath;

    public LocalStorageService(@Value("${aether.storage.local-path:./data/artifacts}") String storagePath) {
        this.rootPath = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            log.error("Failed to initialize storage directory: {}", rootPath, e);
            throw new RuntimeException("Could not create storage directory", e);
        }
    }

    @Override
    public StorageResult store(UUID projectId, String filename, byte[] content) {
        if (content == null) {
            content = new byte[0];
        }
        String hash = computeSha256(content);
        String storageKey = projectId + "/" + UUID.randomUUID() + "_" + (filename != null ? sanitizeFilename(filename) : "artifact.bin");
        Path targetPath = rootPath.resolve(storageKey).normalize();

        // Enforce boundary
        if (!targetPath.startsWith(rootPath)) {
            throw new ApiException("Invalid storage path traversal attempt", HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
            log.info("Stored artifact in storageService. Key={}, size={}, hash={}", storageKey, content.length, hash);
            return new StorageResult(storageKey, hash, content.length);
        } catch (IOException e) {
            log.error("Failed to store artifact file: {}", targetPath, e);
            throw new ApiException("Failed to save artifact content to disk", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public byte[] retrieve(UUID projectId, String storageKey, String expectedHash) {
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new ApiException("Invalid storage path traversal attempt", HttpStatus.BAD_REQUEST);
        }

        if (!Files.exists(targetPath)) {
            throw new ApiException("Artifact content file not found on disk: " + storageKey, HttpStatus.NOT_FOUND);
        }

        try {
            byte[] bytes = Files.readAllBytes(targetPath);
            verifyIntegrity(projectId, storageKey, expectedHash, bytes);
            return bytes;
        } catch (IOException e) {
            log.error("Failed to read artifact file: {}", targetPath, e);
            throw new ApiException("Failed to read artifact content from disk", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void verifyIntegrity(UUID projectId, String storageKey, String expectedHash) {
        retrieve(projectId, storageKey, expectedHash);
    }

    private void verifyIntegrity(UUID projectId, String storageKey, String expectedHash, byte[] content) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return;
        }
        String actualHash = computeSha256(content);
        if (!actualHash.equalsIgnoreCase(expectedHash)) {
            log.error("INTEGRITY COMPROMISED: Storage key {} expected SHA-256 {} but found {}", storageKey, expectedHash, actualHash);
            throw new ApiException("Artifact content hash verification failed! Data tampering or corruption detected.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(content);
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in JVM", e);
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
