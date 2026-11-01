package com.context_platform.aether.integration.security;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class CredentialEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_TAG_LENGTH_BYTES = 16;
    private static final int IV_LENGTH_BYTES = 12;

    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialEncryptionService(KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public EncryptedData encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Cannot encrypt empty or null credential");
        }
        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        return encrypt(plainBytes, plaintext);
    }

    public EncryptedData encrypt(byte[] plaintext, String rawHintForPreview) {
        String keyVersion = keyProvider.getDefaultKeyVersion();
        SecretKey key = keyProvider.getKey(keyVersion);

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] cipherWithTag = cipher.doFinal(plaintext);
            int ciphertextLength = cipherWithTag.length - GCM_TAG_LENGTH_BYTES;

            byte[] ciphertext = Arrays.copyOfRange(cipherWithTag, 0, ciphertextLength);
            byte[] authTag = Arrays.copyOfRange(cipherWithTag, ciphertextLength, cipherWithTag.length);

            String maskedPreview = computeMaskedPreview(rawHintForPreview);

            return new EncryptedData(ciphertext, iv, authTag, keyVersion, maskedPreview);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt integration credential", e);
        }
    }

    public String decryptToString(byte[] ciphertext, byte[] iv, byte[] authTag, String keyVersion) {
        byte[] decrypted = decrypt(ciphertext, iv, authTag, keyVersion);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public byte[] decrypt(byte[] ciphertext, byte[] iv, byte[] authTag, String keyVersion) {
        if (ciphertext == null || iv == null || authTag == null) {
            throw new IllegalArgumentException("Ciphertext, IV, and authTag must not be null");
        }
        if (authTag.length != GCM_TAG_LENGTH_BYTES) {
            throw new IllegalArgumentException("Invalid authTag length: expected " + GCM_TAG_LENGTH_BYTES + " bytes");
        }

        SecretKey key = keyProvider.getKey(keyVersion);

        try {
            byte[] cipherWithTag = new byte[ciphertext.length + authTag.length];
            System.arraycopy(ciphertext, 0, cipherWithTag, 0, ciphertext.length);
            System.arraycopy(authTag, 0, cipherWithTag, ciphertext.length, authTag.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            return cipher.doFinal(cipherWithTag);
        } catch (Exception e) {
            throw new SecurityException("Credential decryption or tag verification failed", e);
        }
    }

    public String computeMaskedPreview(String secret) {
        if (secret == null || secret.isBlank()) {
            return "********";
        }
        String trimmed = secret.trim();
        if (trimmed.length() <= 8) {
            return "********";
        }

        // Check for common prefix patterns
        if (trimmed.startsWith("ghp_") || trimmed.startsWith("gho_") || trimmed.startsWith("ghu_") || trimmed.startsWith("ghs_")) {
            return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
        }
        if (trimmed.startsWith("rnd_")) {
            return "rnd_..." + trimmed.substring(trimmed.length() - 4);
        }
        if (trimmed.startsWith("PMAK-")) {
            return "PMAK-..." + trimmed.substring(trimmed.length() - 4);
        }
        if (trimmed.startsWith("dckr_pat_")) {
            return "dckr_pat_..." + trimmed.substring(trimmed.length() - 4);
        }

        // Generic fallback
        int prefixLen = Math.min(4, trimmed.length() / 4);
        int suffixLen = Math.min(4, trimmed.length() / 4);
        return trimmed.substring(0, prefixLen) + "..." + trimmed.substring(trimmed.length() - suffixLen);
    }
}

