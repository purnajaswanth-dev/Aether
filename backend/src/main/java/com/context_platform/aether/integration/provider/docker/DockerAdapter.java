package com.context_platform.aether.integration.provider.docker;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.integration.adapter.IntegrationAdapter;
import com.context_platform.aether.integration.model.*;
import com.context_platform.aether.integration.reliability.IntegrationReliabilityService;
import com.context_platform.aether.integration.repository.IntegrationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DockerAdapter implements IntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(DockerAdapter.class);

    private final IntegrationConfigRepository configRepository;
    private final IntegrationReliabilityService reliabilityService;
    private final ArtifactService artifactService;
    private final TimelineService timelineService;

    public DockerAdapter(
        IntegrationConfigRepository configRepository,
        IntegrationReliabilityService reliabilityService,
        ArtifactService artifactService,
        TimelineService timelineService
    ) {
        this.configRepository = configRepository;
        this.reliabilityService = reliabilityService;
        this.artifactService = artifactService;
        this.timelineService = timelineService;
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.DOCKER;
    }

    @Override
    public CapabilityDescriptor checkCapabilities() {
        return new CapabilityDescriptor(
            ProviderType.DOCKER,
            "Docker",
            "File-based Dockerfile & Compose container specification synchronization and local agent execution tracking",
            SyncMethod.MANUAL,
            false, // Webhooks unsupported by local Docker daemon
            false, // Polling unsupported
            false, // Direct daemon socket control is unsupported due to privilege escalation risks
            false, // Direct daemon control unsupported
            true,  // Dockerfile and docker-compose.yml files parsed, chunked, and synced
            "None / Local agent filesystem access",
            List.of("dockerfile_sync", "compose_sync"),
            "Local file reading only; direct socket (/var/run/docker.sock) execution is disabled by design",
            "Direct Docker daemon socket control is declared UNSUPPORTED for platform security. Container builds are verified through the local agent command runner ('aether-agent run -- docker build .')."
        );
    }

    @Override
    public IntegrationHealth connect(UUID projectId, Map<String, Object> config, String credential) {
        IntegrationConfigRecord record = new IntegrationConfigRecord(
            UUID.randomUUID(),
            projectId,
            ProviderType.DOCKER,
            IntegrationStatus.CONNECTED,
            config != null ? config : Map.of(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
        configRepository.save(record);

        timelineService.append(
            projectId,
            null,
            "DOCKER",
            "INTEGRATION_CONNECTED",
            null,
            null,
            null,
            "SUCCESS",
            null,
            "SYSTEM",
            "Connected Docker file-based integration",
            "{}"
        );

        return new IntegrationHealth(
            projectId,
            ProviderType.DOCKER,
            IntegrationStatus.CONNECTED,
            Instant.now(),
            Instant.now(),
            "Docker file-based specification sync active",
            config != null ? config : Map.of()
        );
    }

    @Override
    public boolean authenticate(UUID projectId, String credential) {
        // Docker file adapter does not require remote credentials
        return true;
    }

    @Override
    public NormalizedEvent receiveEvents(UUID projectId, String payload, Map<String, String> headers) {
        String eventId = headers.getOrDefault("x-docker-event-id", UUID.randomUUID().toString());
        String eventType = headers.getOrDefault("x-docker-event", "dockerfile_sync");

        return normalizeEvents(projectId, payload, headers);
    }

    @Override
    public NormalizedEvent normalizeEvents(UUID projectId, String rawPayload, Map<String, String> headers) {
        String eventId = headers.getOrDefault("x-docker-event-id", UUID.randomUUID().toString());
        String eventType = headers.getOrDefault("x-docker-event", "dockerfile_sync");
        String filename = headers.getOrDefault("x-file-name", "Dockerfile");

        return new NormalizedEvent(
            eventId,
            eventType,
            ProviderType.DOCKER,
            Instant.now(),
            "Docker file sync: " + filename,
            rawPayload,
            Map.of("filename", filename),
            "docker:" + filename + ":" + eventId
        );
    }

    @Override
    public UUID saveArtifact(UUID projectId, NormalizedEvent event) {
        String filename = (String) event.metadata().getOrDefault("filename", "Dockerfile");
        String wrappedContent = reliabilityService.wrapUntrustedData("DOCKER", event.rawPayload());

        CreateArtifactRequest request = new CreateArtifactRequest(
            filename,
            "ARCHITECTURE_DOC",
            "DOCKER",
            "file:" + filename,
            wrappedContent,
            "Synchronized " + filename + " specification"
        );

        ActorContext actor = ActorContext.system();
        ArtifactResponse response = artifactService.createArtifact(projectId, request, actor);

        timelineService.append(
            projectId,
            null,
            "DOCKER",
            "DOCKER_FILE_SAVED",
            response.id(),
            null,
            1,
            "SUCCESS",
            null,
            "SYSTEM",
            "Saved Docker specification: " + filename,
            "{}"
        );

        return response.id();
    }

    @Override
    public List<NormalizedEvent> retrieve(UUID projectId, String cursor, int limit) {
        return List.of();
    }

    @Override
    public IntegrationHealth syncStatus(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.DOCKER);
        if (configOpt.isEmpty()) {
            return new IntegrationHealth(
                projectId,
                ProviderType.DOCKER,
                IntegrationStatus.DISCONNECTED,
                null,
                Instant.now(),
                "Docker not configured",
                Map.of()
            );
        }

        IntegrationConfigRecord config = configOpt.get();
        return new IntegrationHealth(
            projectId,
            ProviderType.DOCKER,
            config.status(),
            config.lastSyncAt(),
            Instant.now(),
            "Docker file sync ready",
            config.configMetadata()
        );
    }

    @Override
    public void handleError(UUID projectId, Exception error, String context) {
        log.error("Docker Adapter Error [project={}, context={}]: {}", projectId, context, error.getMessage());
    }

    @Override
    public void disconnect(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.DOCKER);
        if (configOpt.isPresent()) {
            configRepository.updateStatus(configOpt.get().id(), IntegrationStatus.DISCONNECTED);
        }
    }
}

