package com.context_platform.aether;

import com.context_platform.aether.ingestion.model.SecretFinding;
import com.context_platform.aether.ingestion.model.SecretFindingType;
import com.context_platform.aether.ingestion.service.SecretDetectorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3: Secret Detection & Redaction Tests")
public class SecretDetectionAndRedactionTest {

    private final SecretDetectorService detector = new SecretDetectorService();
    private final UUID projectId = UUID.randomUUID();
    private final UUID artifactId = UUID.randomUUID();

    @Test
    @DisplayName("Gate Test: AWS Access Key and OpenAI Key are flagged and redacted; raw key never appears in redacted text")
    void testAwsAndOpenAiKeyDetectionAndRedaction() {
        String rawAwsKey = "AKIAIOSFODNN7EXAMPLE";
        String rawOpenAiKey = "sk-abcdef1234567890abcdef1234567890";
        String content = """
            # Cloud Configuration
            aws_access_key: "AKIAIOSFODNN7EXAMPLE"
            openai_api_key: "sk-abcdef1234567890abcdef1234567890"
            endpoint: "https://api.example.com"
            """;

        SecretDetectorService.SecretDetectionResult result = detector.scanAndRedact(
            projectId, artifactId, 1, content
        );

        assertTrue(result.hasSecrets(), "File containing secrets must be flagged");
        assertEquals(2, result.findings().size());

        // Verify findings
        List<SecretFinding> findings = result.findings();
        assertTrue(findings.stream().anyMatch(f -> f.findingType() == SecretFindingType.AWS_KEY));
        assertTrue(findings.stream().anyMatch(f -> f.findingType() == SecretFindingType.OPENAI_KEY));

        // CRITICAL CHECK: Raw keys must NEVER appear in the redacted text or findings
        assertFalse(result.redactedContent().contains(rawAwsKey), "Raw AWS key must never appear in redacted content");
        assertFalse(result.redactedContent().contains(rawOpenAiKey), "Raw OpenAI key must never appear in redacted content");
        assertTrue(result.redactedContent().contains("AKIA****************"));
        assertTrue(result.redactedContent().contains("sk-****************"));
    }

    @Test
    @DisplayName("Detects GitHub Token and Private Key blocks")
    void testGitHubTokenAndPrivateKeyDetection() {
        String rawGitHubToken = "ghp_1234567890abcdef1234567890abcdef123456";
        String content = """
            # CI Secret
            export GITHUB_TOKEN=ghp_1234567890abcdef1234567890abcdef123456
            -----BEGIN RSA PRIVATE KEY-----
            MIIEowIBAAKCAQEA0Y1+
            [FAKE PRIVATE KEY BODY]
            -----END RSA PRIVATE KEY-----
            """;

        SecretDetectorService.SecretDetectionResult result = detector.scanAndRedact(
            projectId, artifactId, 1, content
        );

        assertTrue(result.hasSecrets());
        assertTrue(findingsContain(result.findings(), SecretFindingType.GITHUB_TOKEN));
        assertTrue(findingsContain(result.findings(), SecretFindingType.PRIVATE_KEY));
        assertFalse(result.redactedContent().contains(rawGitHubToken));
        assertTrue(result.redactedContent().contains("[REDACTED PRIVATE KEY]"));
    }

    @Test
    @DisplayName("Shannon entropy correctly calculates randomness")
    void testShannonEntropyCalculation() {
        // Low entropy text (repeated characters)
        double lowEntropy = SecretDetectorService.calculateShannonEntropy("aaaaaaaaaaaaaaaaaaaa");
        assertEquals(0.0, lowEntropy, 0.01);

        // High entropy base64 random secret
        double highEntropy = SecretDetectorService.calculateShannonEntropy("4f8a9B2c7D1e6F0g3H5i8J2k4L6m");
        assertTrue(highEntropy >= 4.0, "Random alphanumeric string must have high entropy");
    }

    private boolean findingsContain(List<SecretFinding> findings, SecretFindingType type) {
        return findings.stream().anyMatch(f -> f.findingType() == type);
    }
}

