package com.context_platform.aether.integration;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.integration.security.CredentialEncryptionService;
import com.context_platform.aether.integration.security.EncryptedData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialEncryptionTest extends BaseTest {

    @Autowired
    private CredentialEncryptionService encryptionService;

    @Test
    @DisplayName("Encrypt and decrypt roundtrip with AES-256-GCM")
    void testEncryptDecryptRoundtrip() {
        String secret = "ghp_1234567890abcdefghijklmnopqrstuvwxyz";

        EncryptedData encrypted = encryptionService.encrypt(secret);

        assertThat(encrypted.ciphertext()).isNotNull();
        assertThat(encrypted.iv()).hasSize(12);
        assertThat(encrypted.authTag()).hasSize(16);
        assertThat(encrypted.keyVersion()).isEqualTo("v1");
        assertThat(encrypted.maskedPreview()).isEqualTo("ghp_...wxyz");

        // Plaintext must never equal ciphertext
        assertThat(new String(encrypted.ciphertext())).isNotEqualTo(secret);

        // Decrypt
        String decrypted = encryptionService.decryptToString(
            encrypted.ciphertext(),
            encrypted.iv(),
            encrypted.authTag(),
            encrypted.keyVersion()
        );

        assertThat(decrypted).isEqualTo(secret);
    }

    @Test
    @DisplayName("Tampered ciphertext or auth tag throws SecurityException")
    void testTamperedCiphertextFails() {
        String secret = "rnd_supersecretproductionkey987654321";
        EncryptedData encrypted = encryptionService.encrypt(secret);

        // Tamper with first byte of ciphertext
        byte[] tamperedCiphertext = encrypted.ciphertext().clone();
        tamperedCiphertext[0] ^= 0x01;

        assertThatThrownBy(() -> encryptionService.decrypt(
            tamperedCiphertext,
            encrypted.iv(),
            encrypted.authTag(),
            encrypted.keyVersion()
        )).isInstanceOf(SecurityException.class);

        // Tamper with auth tag
        byte[] tamperedTag = encrypted.authTag().clone();
        tamperedTag[0] ^= 0x01;

        assertThatThrownBy(() -> encryptionService.decrypt(
            encrypted.ciphertext(),
            encrypted.iv(),
            tamperedTag,
            encrypted.keyVersion()
        )).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Masked preview formats correctly across providers without leaking secrets")
    void testMaskedPreviews() {
        assertThat(encryptionService.computeMaskedPreview("ghp_9876543210abcdef")).isEqualTo("ghp_...cdef");
        assertThat(encryptionService.computeMaskedPreview("rnd_1122334455667788")).isEqualTo("rnd_...7788");
        assertThat(encryptionService.computeMaskedPreview("PMAK-64f9b8c2a1e0456789")).isEqualTo("PMAK-...6789");
        assertThat(encryptionService.computeMaskedPreview("short")).isEqualTo("********");
    }
}

