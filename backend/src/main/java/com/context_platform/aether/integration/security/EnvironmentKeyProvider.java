package com.context_platform.aether.integration.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EnvironmentKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentKeyProvider.class);
    private static final String DEFAULT_VERSION = "v1";

    private final Map<String, SecretKey> keyStore = new ConcurrentHashMap<>();

    public EnvironmentKeyProvider(@Value("${aether.security.master-key:}") String configuredKey) {
        String envKey = System.getenv("AETHER_MASTER_KEY");
        String rawKey = (envKey != null && !envKey.isBlank()) ? envKey : configuredKey;

        SecretKey secretKey;
        if (rawKey != null && !rawKey.isBlank()) {
            secretKey = parseSecretKey(rawKey.trim());
            log.info("Initialized EnvironmentKeyProvider with configured master key for version {}", DEFAULT_VERSION);
        } else {
            // Safe deterministic fallback for development and integration test harness
            log.warn("AETHER_MASTER_KEY not found in environment or properties; using development fallback key.");
            secretKey = deriveFallbackKey("aether-development-master-key-seed-2026");
        }
        keyStore.put(DEFAULT_VERSION, secretKey);
    }

    @Override
    public SecretKey getKey(String keyVersion) {
        SecretKey key = keyStore.get(keyVersion);
        if (key == null) {
            throw new IllegalArgumentException("Unknown encryption key version: " + keyVersion);
        }
        return key;
    }

    @Override
    public String getDefaultKeyVersion() {
        return DEFAULT_VERSION;
    }

    private SecretKey parseSecretKey(String raw) {
        byte[] keyBytes;
        if (raw.length() == 64 && raw.matches("^[0-9a-fA-F]+$")) {
            keyBytes = hexStringToByteArray(raw);
        } else {
            try {
                byte[] decoded = Base64.getDecoder().decode(raw);
                if (decoded.length == 32) {
                    keyBytes = decoded;
                } else {
                    keyBytes = hashTo32Bytes(raw);
                }
            } catch (IllegalArgumentException e) {
                keyBytes = hashTo32Bytes(raw);
            }
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private SecretKey deriveFallbackKey(String seed) {
        return new SecretKeySpec(hashTo32Bytes(seed), "AES");
    }

    private byte[] hashTo32Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}

