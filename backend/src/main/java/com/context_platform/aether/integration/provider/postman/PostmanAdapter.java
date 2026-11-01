package com.context_platform.aether.integration.provider.postman;

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

import java.time.Instant;
import java.util.*;

@Component
public class PostmanAdapter implements IntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(PostmanAdapter.class);

    private final IntegrationConfigRepository configRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final IntegrationCheckpointRepository checkpointRepository;
    private final CredentialEncryptionService encryptionService;
    private final IntegrationReliabilityService reliabilityService;
    private final ArtifactService artifactService;
    private final TimelineService timelineService;
    private final ObjectMapper objectMapper;

    public PostmanAdapter(
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
        return ProviderType.POSTMAN;
    }

    @Override
    public CapabilityDescriptor checkCapabilities() {
        return new CapabilityDescriptor(
            ProviderType.POSTMAN,
            "Postman",
            "Postman API collections and OpenAPI schema synchronization",
            SyncMethod.POLLING,
            false, // Webhooks not supported by Postman Public API
            true,  // Polling supported via Postman REST API
            false, // No logs
            false, // No deployments
            true,  // Collections and API schemas synced as artifacts
            "Postman API Key (PMAK-...)",
            List.of("collection_sync", "schema_sync"),
            "Postman API rate limits: 300 requests per minute per API key",
            "Constitution Rule 8 enforcement: environment downloads (/environments) are strictly prohibited to prevent API keys and credential leakage into memory."
        );
    }

    @Override
    public IntegrationHealth connect(UUID projectId, Map<String, Object> config, String credential) {
        // Enforce Constitution Rule 8: Never allow syncType = 'environments'
        if (config != null && "environments".equalsIgnoreCase((String) config.get("syncType"))) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Constitution Rule 8 Violation: Postman environment synchronization is prohibited to protect secret credentials."
            );
        }

        IntegrationConfigRecord record = new IntegrationConfigRecord(
            UUID.randomUUID(),
            projectId,
            ProviderType.POSTMAN,
            IntegrationStatus.CONNECTED,
            config != null ? config : Map.of(),
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
            "POSTMAN",
            "INTEGRATION_CONNECTED",
            null,
            null,
            null,
            "SUCCESS",
            null,
            "SYSTEM",
            "Connected Postman integration",
            "{}"
        );

        return new IntegrationHealth(
            projectId,
            ProviderType.POSTMAN,
            IntegrationStatus.CONNECTED,
            Instant.now(),
            Instant.now(),
            "Postman connected for collections and schema sync",
            config != null ? config : Map.of()
        );
    }

    @Override
    public boolean authenticate(UUID projectId, String credential) {
        if (credential == null || credential.isBlank()) {
            return false;
        }
        return credential.startsWith("PMAK-") || credential.length() >= 20;
    }

    @Override
    public NormalizedEvent receiveEvents(UUID projectId, String payload, Map<String, String> headers) {
        String eventId = headers.getOrDefault("x-postman-event-id", UUID.randomUUID().toString());
        String eventType = headers.getOrDefault("x-postman-event", "collection_sync");

        String idempotencyKey = "postman:" + eventType + ":" + eventId;
        if (reliabilityService.isDuplicate(projectId, ProviderType.POSTMAN, eventId, idempotencyKey)) {
            log.info("Duplicate Postman event skipped: {}", eventId);
            return null;
        }

        return normalizeEvents(projectId, payload, headers);
    }

    @Override
    public NormalizedEvent normalizeEvents(UUID projectId, String rawPayload, Map<String, String> headers) {
        String eventId = headers.getOrDefault("x-postman-event-id", UUID.randomUUID().toString());
        String eventType = headers.getOrDefault("x-postman-event", "collection_sync");

        Map<String, Object> data = Map.of();
        String summary = "Postman collection sync event";
        if (rawPayload != null && !rawPayload.isBlank()) {
            try {
                data = objectMapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
                Map<?, ?> info = (Map<?, ?>) data.get("info");
                if (info != null && info.get("name") != null) {
                    summary = "Postman Collection: " + info.get("name");
                }
            } catch (Exception e) {
                log.warn("Failed to parse Postman payload: {}", e.getMessage());
            }
        }

        return new NormalizedEvent(
            eventId,
            eventType,
            ProviderType.POSTMAN,
            Instant.now(),
            summary,
            rawPayload,
            data,
            "postman:" + eventType + ":" + eventId
        );
    }

    @Override
    public UUID saveArtifact(UUID projectId, NormalizedEvent event) {
        String wrappedContent = reliabilityService.wrapUntrustedData("POSTMAN", event.rawPayload());
        String title = event.summary();

        CreateArtifactRequest request = new CreateArtifactRequest(
            title,
            "API_SPEC",
            "POSTMAN",
            "postman:" + event.eventId(),
            wrappedContent,
            "Synced from Postman: " + event.summary()
        );

        ActorContext actor = ActorContext.system();
        ArtifactResponse response = artifactService.createArtifact(projectId, request, actor);

        timelineService.append(
            projectId,
            null,
            "POSTMAN",
            "POSTMAN_COLLECTION_SAVED",
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
        checkpointRepository.saveCheckpoint(projectId, ProviderType.POSTMAN, "collections", String.valueOf(Instant.now().toEpochMilli()));
        return List.of();
    }

    @Override
    public IntegrationHealth syncStatus(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.POSTMAN);
        if (configOpt.isEmpty()) {
            return new IntegrationHealth(
                projectId,
                ProviderType.POSTMAN,
                IntegrationStatus.DISCONNECTED,
                null,
                Instant.now(),
                "Postman not connected",
                Map.of()
            );
        }

        IntegrationConfigRecord config = configOpt.get();
        return new IntegrationHealth(
            projectId,
            ProviderType.POSTMAN,
            config.status(),
            config.lastSyncAt(),
            Instant.now(),
            "Postman connected and healthy",
            config.configMetadata()
        );
    }

    @Override
    public void handleError(UUID projectId, Exception error, String context) {
        log.error("Postman Adapter Error [project={}, context={}]: {}", projectId, context, error.getMessage());
    }

    @Override
    public void disconnect(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.POSTMAN);
        if (configOpt.isPresent()) {
            credentialRepository.deleteByIntegrationId(configOpt.get().id());
            configRepository.updateStatus(configOpt.get().id(), IntegrationStatus.DISCONNECTED);
        }
        timelineService.append(
            projectId,
            null,
            "POSTMAN",
            "INTEGRATION_DISCONNECTED",
            null,
            null,
            null,
            "SUCCESS",
            null,
            "SYSTEM",
            "Disconnected Postman integration",
            "{}"
        );
    }
}

