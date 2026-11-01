package com.context_platform.aether.integration.controller;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.integration.adapter.IntegrationAdapter;
import com.context_platform.aether.integration.model.NormalizedEvent;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.registry.IntegrationRegistry;
import com.context_platform.aether.integration.reliability.IntegrationReliabilityService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final IntegrationRegistry registry;
    private final IntegrationReliabilityService reliabilityService;

    public WebhookController(
        IntegrationRegistry registry,
        IntegrationReliabilityService reliabilityService
    ) {
        this.registry = registry;
        this.reliabilityService = reliabilityService;
    }

    @PostMapping("/{providerStr}/{projectId}")
    public ResponseEntity<Map<String, Object>> handleWebhook(
        @PathVariable String providerStr,
        @PathVariable UUID projectId,
        @RequestBody(required = false) String payload,
        HttpServletRequest request
    ) {
        ProviderType provider;
        try {
            provider = ProviderType.valueOf(providerStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Unknown integration provider: " + providerStr));
        }

        // Collect request headers
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName.toLowerCase(), request.getHeader(headerName));
            }
        }

        IntegrationAdapter adapter = registry.getAdapter(provider);
        String eventType = headers.getOrDefault("x-github-event", headers.getOrDefault("x-event-type", "webhook"));

        try {
            NormalizedEvent event = adapter.receiveEvents(projectId, payload != null ? payload : "", headers);
            if (event == null) {
                return ResponseEntity.ok(Map.of(
                    "status", "DUPLICATE_SKIPPED",
                    "message", "Event has already been processed"
                ));
            }

            UUID artifactId = adapter.saveArtifact(projectId, event);
            reliabilityService.recordProcessed(
                projectId,
                provider,
                event.eventId(),
                event.eventType(),
                event.idempotencyKey(),
                artifactId
            );

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "eventId", event.eventId(),
                "artifactId", artifactId
            ));

        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED || e.getStatus() == HttpStatus.FORBIDDEN) {
                log.warn("Webhook authentication failed for provider={}, project={}: {}", provider, projectId, e.getMessage());
                return ResponseEntity.status(e.getStatus()).body(Map.of(
                    "status", "UNAUTHORIZED",
                    "error", e.getMessage()
                ));
            }

            // Route non-auth failures to DLQ
            var dlq = reliabilityService.sendToDeadLetter(
                projectId,
                provider,
                eventType,
                payload != null ? payload : "",
                headers,
                e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "ROUTED_TO_DEAD_LETTER",
                "deadLetterId", dlq.id(),
                "error", e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Webhook processing error for provider={}, project={}: {}", provider, projectId, e.getMessage(), e);
            var dlq = reliabilityService.sendToDeadLetter(
                projectId,
                provider,
                eventType,
                payload != null ? payload : "",
                headers,
                e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "ROUTED_TO_DEAD_LETTER",
                "deadLetterId", dlq.id(),
                "error", e.getMessage()
            ));
        }
    }
}

