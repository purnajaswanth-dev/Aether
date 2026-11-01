package com.context_platform.aether.integration;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.integration.model.NormalizedEvent;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.provider.github.GitHubAdapter;
import com.context_platform.aether.integration.reliability.IntegrationReliabilityService;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureAndSecurityTest extends BaseTest {

    @Autowired
    private IntegrationReliabilityService reliabilityService;

    @Autowired
    private GitHubAdapter gitHubAdapter;

    @Test
    @DisplayName("HMAC-SHA256 signature verification accepts valid and rejects tampered")
    void testHmacSha256Verification() throws Exception {
        String secret = "test-webhook-secret-42";
        String payload = "{\"ref\":\"refs/heads/main\",\"commits\":[{\"id\":\"abc1234\"}]}";

        // Compute valid signature
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hexSignature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        // Valid signature with sha256= prefix
        boolean valid = reliabilityService.verifyHmacSha256(
            payload.getBytes(StandardCharsets.UTF_8),
            secret,
            "sha256=" + hexSignature,
            "sha256="
        );
        assertThat(valid).isTrue();

        // Tampered payload
        boolean tampered = reliabilityService.verifyHmacSha256(
            (payload + "tampered").getBytes(StandardCharsets.UTF_8),
            secret,
            "sha256=" + hexSignature,
            "sha256="
        );
        assertThat(tampered).isFalse();

        // Invalid signature header
        boolean invalidSig = reliabilityService.verifyHmacSha256(
            payload.getBytes(StandardCharsets.UTF_8),
            secret,
            "sha256=0000000000000000000000000000000000000000000000000000000000000000",
            "sha256="
        );
        assertThat(invalidSig).isFalse();
    }

    @Test
    @DisplayName("GitHubAdapter enforces HMAC verification and detects duplicate delivery replay")
    void testGitHubAdapterSecurityAndReplay() throws Exception {
        AuthResponse user = registerUser("gh_sec@example.com", "Password123!", "GH Admin");
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("GH Security Proj", "GitHub security test", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        String secret = "super-secret-hmac-key";
        gitHubAdapter.connect(project.id(), Map.of("repo", "acme/backend"), secret);

        String deliveryId = UUID.randomUUID().toString();
        String payload = "{\"ref\":\"refs/heads/feature\",\"action\":\"push\"}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String validSig = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        // 1. Process valid event
        NormalizedEvent event = gitHubAdapter.receiveEvents(project.id(), payload, Map.of(
            "x-github-delivery", deliveryId,
            "x-github-event", "push",
            "x-hub-signature-256", validSig
        ));

        assertThat(event).isNotNull();
        assertThat(event.eventId()).isEqualTo(deliveryId);

        // Save artifact and record processed
        UUID artifactId = gitHubAdapter.saveArtifact(project.id(), event);
        reliabilityService.recordProcessed(
            project.id(),
            ProviderType.GITHUB,
            event.eventId(),
            event.eventType(),
            event.idempotencyKey(),
            artifactId
        );

        // 2. Replay duplicate event -> returns null (deduplicated)
        NormalizedEvent replay = gitHubAdapter.receiveEvents(project.id(), payload, Map.of(
            "x-github-delivery", deliveryId,
            "x-github-event", "push",
            "x-hub-signature-256", validSig
        ));
        assertThat(replay).isNull();

        // 3. Forged signature -> throws 401 Unauthorized
        assertThatThrownBy(() -> gitHubAdapter.receiveEvents(project.id(), payload, Map.of(
            "x-github-delivery", UUID.randomUUID().toString(),
            "x-github-event", "push",
            "x-hub-signature-256", "sha256=forged_bad_hash"
        ))).isInstanceOf(ApiException.class);
    }
}
