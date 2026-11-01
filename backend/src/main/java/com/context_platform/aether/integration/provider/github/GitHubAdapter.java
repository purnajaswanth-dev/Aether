package com.context_platform.aether.integration.provider.github;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.integration.adapter.IntegrationAdapter;
import com.context_platform.aether.integration.model.*;
import com.context_platform.aether.integration.reliability.IntegrationReliabilityService;
import com.context_platform.aether.integration.repository.IntegrationCheckpointRepository;
import com.context_platform.aether.integration.repository.IntegrationConfigRepository;
import com.context_platform.aether.integration.repository.IntegrationCredentialRepository;
import com.context_platform.aether.integration.security.CredentialEncryptionService;
import com.context_platform.aether.integration.security.EncryptedData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class GitHubAdapter implements IntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(GitHubAdapter.class);

    private final IntegrationConfigRepository configRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final IntegrationCheckpointRepository checkpointRepository;
    private final CredentialEncryptionService encryptionService;
    private final IntegrationReliabilityService reliabilityService;
    private final ArtifactService artifactService;
    private final TimelineService timelineService;
    private final ObjectMapper objectMapper;

    public GitHubAdapter(
        IntegrationConfigRepository configRepository,
        IntegrationCredentialRepository credentialRepository,
        IntegrationCheckpointRepository checkpointRepository,
        CredentialEncryptionService encryptionService,
        IntegrationReliabilityService reliabilityService,
        ArtifactService artifactService,
        TimelineService timelineService,
        ObjectMapper objectMapper
    ) {
        this.configRepository = configRepository;
        this.credentialRepository = credentialRepository;
        this.checkpointRepository = checkpointRepository;
        this.encryptionService = encryptionService;
        this.reliabilityService = reliabilityService;
        this.artifactService = artifactService;
        this.timelineService = timelineService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GITHUB;
    }

    @Override
    public CapabilityDescriptor checkCapabilities() {
        return new CapabilityDescriptor(
            ProviderType.GITHUB,
            "GitHub",
            "Bi-directional GitHub integration for repository events, commits, PRs, and workflow runs",
            SyncMethod.WEBHOOK,
            true, // webhooks
            true, // polling
            true, // logs (Actions workflow runs)
            true, // deployments
            true, // artifact sync
            "GitHub App installation token or Personal Access Token (Bearer), Webhook HMAC-SHA256 secret",
            List.of("push", "pull_request", "check_run", "workflow_run", "release"),
            "5,000 requests per hour for authenticated installations / users",
            "Webhook signature replay window of 5 minutes; log retention subject to repository Actions retention limits (max 90 days)"
        );
    }

    @Override
    public IntegrationHealth connect(UUID projectId, Map<String, Object> config, String credential) {
        if (config == null || !config.containsKey("repo")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GitHub configuration requires 'repo' (e.g. 'owner/repo')");
        }

        // Save or update configuration
        IntegrationConfigRecord record = new IntegrationConfigRecord(
            UUID.randomUUID(),
            projectId,
            ProviderType.GITHUB,
            IntegrationStatus.CONNECTED,
            config,
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
        IntegrationConfigRecord savedConfig = configRepository.save(record);

        if (credential != null && !credential.isBlank()) {
            EncryptedData encrypted = encryptionService.encrypt(credential);
            credentialRepository.save(projectId, savedConfig.id(), encrypted);
        }

        timelineService.append(
            projectId,
            null,
            "GITHUB",
            "INTEGRATION_CONNECTED",
            null,
            null,
            null,
            "SUCCESS",
            null,
            "SYSTEM",
            "Connected GitHub repository: " + config.get("repo"),
            "{}"
        );

        return new IntegrationHealth(
            projectId,
            ProviderType.GITHUB,
            IntegrationStatus.CONNECTED,
            Instant.now(),
            Instant.now(),
            "GitHub integration connected to " + config.get("repo"),
            Map.of("repo", config.get("repo"))
        );
    }

    @Override
    public boolean authenticate(UUID projectId, String credential) {
        if (credential == null || credential.isBlank()) {
            return false;
        }
        return credential.startsWith("ghp_") || credential.startsWith("gho_") || credential.length() >= 16;
    }

    @Override
    public NormalizedEvent receiveEvents(UUID projectId, String payload, Map<String, String> headers) {
        String eventId = headers.getOrDefault("x-github-delivery", headers.getOrDefault("X-GitHub-Delivery", UUID.randomUUID().toString()));
        String eventType = headers.getOrDefault("x-github-event", headers.getOrDefault("X-GitHub-Event", "push"));
        String signatureHeader = headers.getOrDefault("x-hub-signature-256", headers.getOrDefault("X-Hub-Signature-256", null));

        // Retrieve webhook secret if configured
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.GITHUB);
        if (configOpt.isPresent()) {
            var credOpt = credentialRepository.findByIntegrationId(configOpt.get().id());
            if (credOpt.isPresent() && signatureHeader != null) {
                var cred = credOpt.get();
                String webhookSecret = encryptionService.decryptToString(
                    cred.encryptedSecret(), cred.initializationVector(), cred.authTag(), cred.keyVersion()
                );
                byte[] payloadBytes = payload != null ? payload.getBytes(StandardCharsets.UTF_8) : new byte[0];
                boolean valid = reliabilityService.verifyHmacSha256(payloadBytes, webhookSecret, signatureHeader, "sha256=");
                if (!valid) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid GitHub webhook HMAC-SHA256 signature");
                }
            }
        }

        String idempotencyKey = "github:" + eventType + ":" + eventId;
        if (reliabilityService.isDuplicate(projectId, ProviderType.GITHUB, eventId, idempotencyKey)) {
            log.info("Duplicate GitHub event skipped: delivery={}", eventId);
            return null;
        }

        return normalizeEvents(projectId, payload, headers);
    }

    @Override
    public NormalizedEvent normalizeEvents(UUID projectId, String rawPayload, Map<String, String> headers) {
        String eventId = headers.getOrDefault("x-github-delivery", headers.getOrDefault("X-GitHub-Delivery", UUID.randomUUID().toString()));
        String eventType = headers.getOrDefault("x-github-event", headers.getOrDefault("X-GitHub-Event", "push"));

        Map<String, Object> data = Map.of();
        String summary = "GitHub " + eventType + " event";
        if (rawPayload != null && !rawPayload.isBlank()) {
            try {
                data = objectMapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
                if ("push".equalsIgnoreCase(eventType)) {
                    String ref = (String) data.get("ref");
                    summary = "GitHub Push to " + (ref != null ? ref : "unknown branch");
                } else if ("pull_request".equalsIgnoreCase(eventType)) {
                    String action = (String) data.get("action");
                    Map<?, ?> pr = (Map<?, ?>) data.get("pull_request");
                    String title = pr != null ? (String) pr.get("title") : "";
                    summary = "GitHub PR [" + action + "]: " + title;
                } else if ("check_run".equalsIgnoreCase(eventType)) {
                    String action = (String) data.get("action");
                    Map<?, ?> check = (Map<?, ?>) data.get("check_run");
                    String name = check != null ? (String) check.get("name") : "";
                    String conclusion = check != null ? (String) check.get("conclusion") : "";
                    summary = "GitHub CheckRun [" + name + "]: " + conclusion;
                }
            } catch (Exception e) {
                log.warn("Failed to parse GitHub JSON payload: {}", e.getMessage());
            }
        }

        return new NormalizedEvent(
            eventId,
            eventType,
            ProviderType.GITHUB,
            Instant.now(),
            summary,
            rawPayload,
            data,
            "github:" + eventType + ":" + eventId
        );
    }

    @Override
    public UUID saveArtifact(UUID projectId, NormalizedEvent event) {
        String wrappedContent = reliabilityService.wrapUntrustedData("GITHUB", event.rawPayload());
        String title = "GitHub " + event.eventType() + " - " + event.eventId().substring(0, Math.min(8, event.eventId().length()));

        CreateArtifactRequest request = new CreateArtifactRequest(
            title,
            "LOG",
            "GITHUB",
            "delivery:" + event.eventId(),
            wrappedContent,
            event.summary()
        );

        ActorContext actor = ActorContext.system();
        ArtifactResponse response = artifactService.createArtifact(projectId, request, actor);

        timelineService.append(
            projectId,
            null,
            "GITHUB",
            "GITHUB_" + event.eventType().toUpperCase(),
            response.id(),
            null,
            1,
            "SUCCESS",
            null,
            "SYSTEM",
            event.summary(),
            "{}"
        );

        return response.id();
    }

    @Override
    public List<NormalizedEvent> retrieve(UUID projectId, String cursor, int limit) {
        checkpointRepository.saveCheckpoint(projectId, ProviderType.GITHUB, "events", String.valueOf(Instant.now().toEpochMilli()));
        return List.of();
    }

    @Override
    public IntegrationHealth syncStatus(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.GITHUB);
        if (configOpt.isEmpty()) {
            return new IntegrationHealth(
                projectId,
                ProviderType.GITHUB,
                IntegrationStatus.DISCONNECTED,
                null,
                Instant.now(),
                "GitHub not connected",
                Map.of()
            );
        }

        IntegrationConfigRecord config = configOpt.get();
        return new IntegrationHealth(
            projectId,
            ProviderType.GITHUB,
            config.status(),
            config.lastSyncAt(),
            Instant.now(),
            "GitHub connected and healthy",
            config.configMetadata()
        );
    }

    @Override
    public void handleError(UUID projectId, Exception error, String context) {
        log.error("GitHub Adapter Error [project={}, context={}]: {}", projectId, context, error.getMessage());
    }

    @Override
    public void disconnect(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.GITHUB);
        if (configOpt.isPresent()) {
            credentialRepository.deleteByIntegrationId(configOpt.get().id());
            configRepository.updateStatus(configOpt.get().id(), IntegrationStatus.DISCONNECTED);
        }
        timelineService.append(
            projectId,
            null,
            "GITHUB",
            "INTEGRATION_DISCONNECTED",
            null,
            null,
            null,
            "SUCCESS",
            null,
            "SYSTEM",
            "Disconnected GitHub integration",
            "{}"
        );
    }
}

