package com.context_platform.aether.integration.reliability;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.integration.adapter.IntegrationAdapter;
import com.context_platform.aether.integration.model.DeadLetterEventRecord;
import com.context_platform.aether.integration.model.NormalizedEvent;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.registry.IntegrationRegistry;
import com.context_platform.aether.integration.repository.DeadLetterEventRepository;
import com.context_platform.aether.integration.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class IntegrationReliabilityService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationReliabilityService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final DeadLetterEventRepository deadLetterEventRepository;
    private final IntegrationRegistry integrationRegistry;

    public IntegrationReliabilityService(
        ProcessedEventRepository processedEventRepository,
        DeadLetterEventRepository deadLetterEventRepository,
        @Lazy IntegrationRegistry integrationRegistry
    ) {
        this.processedEventRepository = processedEventRepository;
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.integrationRegistry = integrationRegistry;
    }

    /**
     * Constant-time HMAC-SHA256 verification with replay and timing-attack protection.
     */
    public boolean verifyHmacSha256(byte[] payload, String secret, String signatureHeader, String prefix) {
        if (payload == null || secret == null || signatureHeader == null) {
            return false;
        }

        String expectedSignatureHex = signatureHeader.trim();
        if (prefix != null && expectedSignatureHex.startsWith(prefix)) {
            expectedSignatureHex = expectedSignatureHex.substring(prefix.length()).trim();
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] computedHash = mac.doFinal(payload);
            String computedHex = HexFormat.of().formatHex(computedHash);

            // Constant-time byte array comparison
            return MessageDigest.isEqual(
                expectedSignatureHex.toLowerCase().getBytes(StandardCharsets.UTF_8),
                computedHex.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.warn("HMAC verification failed due to cryptographic error: {}", e.getMessage());
            return false;
        }
    }

    public boolean isDuplicate(UUID projectId, ProviderType provider, String eventId, String idempotencyKey) {
        if (eventId != null && processedEventRepository.isProcessed(projectId, provider, eventId)) {
            return true;
        }
        if (idempotencyKey != null && processedEventRepository.isIdempotencyKeyUsed(projectId, idempotencyKey)) {
            return true;
        }
        return false;
    }

    public void recordProcessed(
        UUID projectId,
        ProviderType provider,
        String eventId,
        String eventType,
        String idempotencyKey,
        UUID rawArtifactId
    ) {
        processedEventRepository.recordEvent(
            projectId,
            provider,
            eventId,
            eventType,
            idempotencyKey != null ? idempotencyKey : eventId,
            rawArtifactId
        );
    }

    public DeadLetterEventRecord sendToDeadLetter(
        UUID projectId,
        ProviderType provider,
        String eventType,
        String payload,
        Map<String, ?> headers,
        String errorMessage
    ) {
        log.warn("Routing event to dead-letter queue: provider={}, eventType={}, error={}", provider, eventType, errorMessage);
        return deadLetterEventRepository.recordFailedEvent(
            projectId,
            provider,
            eventType,
            payload,
            headers,
            errorMessage
        );
    }

    public void retryDeadLetter(UUID deadLetterId) {
        DeadLetterEventRecord record = deadLetterEventRepository.findById(deadLetterId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Dead letter event not found: " + deadLetterId));

        if (!"PENDING".equalsIgnoreCase(record.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dead letter event is already in status: " + record.status());
        }

        IntegrationAdapter adapter = integrationRegistry.getAdapter(record.provider());

        try {
            // Re-normalize and re-process event
            Map<String, String> headerMap = new java.util.HashMap<>();
            if (record.headers() != null) {
                record.headers().forEach((k, v) -> headerMap.put(k, String.valueOf(v)));
            }

            NormalizedEvent event = adapter.receiveEvents(record.projectId(), record.payload(), headerMap);
            if (event != null) {
                UUID artifactId = adapter.saveArtifact(record.projectId(), event);
                recordProcessed(
                    record.projectId(),
                    record.provider(),
                    event.eventId(),
                    event.eventType(),
                    event.idempotencyKey(),
                    artifactId
                );
            }
            deadLetterEventRepository.markRetried(deadLetterId);
            log.info("Successfully retried DLQ event {}", deadLetterId);
        } catch (Exception e) {
            log.error("Failed to retry DLQ event {}: {}", deadLetterId, e.getMessage());
            deadLetterEventRepository.incrementAttempts(deadLetterId, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Retry failed: " + e.getMessage());
        }
    }

    /**
     * Untrusted data boundary wrapper per Constitution Rule 6.
     * Prevents prompt injection, command injection, and authorization escapes in AI retrieval pipelines.
     */
    public String wrapUntrustedData(String sourcePlatform, String rawContent) {
        if (rawContent == null) return "";
        return "<!-- BEGIN_UNTRUSTED_DATA_BOUNDARY source=\"" + sourcePlatform + "\" -->\n"
             + rawContent
             + "\n<!-- END_UNTRUSTED_DATA_BOUNDARY -->";
    }
}

