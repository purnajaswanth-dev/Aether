package com.context_platform.aether.integration.provider.render;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.integration.adapter.IntegrationAdapter;
import com.context_platform.aether.integration.model.*;
import com.context_platform.aether.integration.reliability.IntegrationReliabilityService;
import com.context_platform.aether.integration.repository.DeploymentRepository;
import com.context_platform.aether.integration.repository.IntegrationCheckpointRepository;
import com.context_platform.aether.integration.repository.IntegrationConfigRepository;
import com.context_platform.aether.integration.repository.IntegrationCredentialRepository;
import com.context_platform.aether.integration.security.CredentialEncryptionService;
import com.context_platform.aether.integration.security.EncryptedData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class RenderAdapter implements IntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(RenderAdapter.class);

    private final IntegrationConfigRepository configRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final IntegrationCheckpointRepository checkpointRepository;
    private final DeploymentRepository deploymentRepository;
    private final CredentialEncryptionService encryptionService;
    private final IntegrationReliabilityService reliabilityService;
    private final ArtifactService artifactService;
    private final TimelineService timelineService;
    private final ObjectMapper objectMapper;

    private WebClient webClient;
    private String renderApiBaseUrl = "https://api.render.com/v1";

    // Failure extraction patterns
    private static final Pattern BUILD_ERROR_PATTERN = Pattern.compile("(?i)(build failed|npm ERR!|error TS|compilation error|maven.*failed|exit code 1|docker build.*failed)");
    private static final Pattern RUNTIME_CRASH_PATTERN = Pattern.compile("(?i)(panic:|out of memory|fatal error|segmentation fault|connection refused|uncaught exception)");
    private static final Pattern HEALTH_CHECK_PATTERN = Pattern.compile("(?i)(health check failed|timed out waiting for service to respond|unhealthy)");

    public RenderAdapter(
        IntegrationConfigRepository configRepository,
        IntegrationCredentialRepository credentialRepository,
        IntegrationCheckpointRepository checkpointRepository,
        DeploymentRepository deploymentRepository,
        CredentialEncryptionService encryptionService,
        IntegrationReliabilityService reliabilityService,
        ArtifactService artifactService,
        TimelineService timelineService,
        ObjectMapper objectMapper
    ) {
        this.configRepository = configRepository;
        this.credentialRepository = credentialRepository;
        this.checkpointRepository = checkpointRepository;
        this.deploymentRepository = deploymentRepository;
        this.encryptionService = encryptionService;
        this.reliabilityService = reliabilityService;
        this.artifactService = artifactService;
        this.timelineService = timelineService;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(renderApiBaseUrl).build();
    }

    public void setRenderApiBaseUrl(String baseUrl) {
        this.renderApiBaseUrl = baseUrl;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.RENDER;
    }

    @Override
    public CapabilityDescriptor checkCapabilities() {
        return new CapabilityDescriptor(
            ProviderType.RENDER,
            "Render",
            "Render cloud deployment tracking, service health, and build/runtime log synchronization",
            SyncMethod.POLLING,
            false, // Render deploy hooks are inbound-only; no outbound lifecycle webhooks exist
            true,  // Polling supported via Render REST API v1
            true,  // Build & runtime logs accessible via API
            true,  // Deploys trackable via API
            true,  // Logs synced as immutable artifacts
            "Render API Key (rnd_...)",
            List.of("deploy_created", "build_in_progress", "live", "build_failed", "deploy_failed", "canceled"),
            "Render REST API rate limit: 100 requests per minute per API key",
            "Render outbound webhooks are unsupported by Render; deployment tracking uses cursor polling. Log retention: 7 days (free) / 30 days (paid)."
        );
    }

    @Override
    public IntegrationHealth connect(UUID projectId, Map<String, Object> config, String credential) {
        if (config == null || !config.containsKey("serviceId")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Render configuration requires 'serviceId' (e.g. 'srv-c8...')");
        }

        IntegrationConfigRecord record = new IntegrationConfigRecord(
            UUID.randomUUID(),
            projectId,
            ProviderType.RENDER,
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
            "RENDER",
            "INTEGRATION_CONNECTED",
            null,
            null,
            null,
            "SUCCESS",
            null,
            "SYSTEM",
            "Connected Render service: " + config.get("serviceId"),
            "{}"
        );

        return new IntegrationHealth(
            projectId,
            ProviderType.RENDER,
            IntegrationStatus.CONNECTED,
            Instant.now(),
            Instant.now(),
            "Render integration connected to service " + config.get("serviceId"),
            Map.of("serviceId", config.get("serviceId"))
        );
    }

    @Override
    public boolean authenticate(UUID projectId, String credential) {
        if (credential == null || credential.isBlank()) {
            return false;
        }
        return credential.startsWith("rnd_") || credential.length() >= 20;
    }

    @Override
    public NormalizedEvent receiveEvents(UUID projectId, String payload, Map<String, String> headers) {
        // Render doesn't have native outbound webhooks, but manual test payloads or proxy events can enter here
        String eventId = headers.getOrDefault("x-render-event-id", UUID.randomUUID().toString());
        String eventType = headers.getOrDefault("x-render-event", "deploy");

        String idempotencyKey = "render:" + eventType + ":" + eventId;
        if (reliabilityService.isDuplicate(projectId, ProviderType.RENDER, eventId, idempotencyKey)) {
            log.info("Duplicate Render event skipped: {}", eventId);
            return null;
        }

        return normalizeEvents(projectId, payload, headers);
    }

    @Override
    public NormalizedEvent normalizeEvents(UUID projectId, String rawPayload, Map<String, String> headers) {
        String eventId = headers.getOrDefault("x-render-event-id", UUID.randomUUID().toString());
        String eventType = headers.getOrDefault("x-render-event", "deploy");

        Map<String, Object> data = Map.of();
        String summary = "Render deployment event";
        if (rawPayload != null && !rawPayload.isBlank()) {
            try {
                data = objectMapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {});
                String status = (String) data.getOrDefault("status", "unknown");
                String deployId = (String) data.getOrDefault("id", eventId);
                summary = "Render Deploy [" + deployId + "]: " + status;
            } catch (Exception e) {
                log.warn("Failed to parse Render JSON payload: {}", e.getMessage());
            }
        }

        return new NormalizedEvent(
            eventId,
            eventType,
            ProviderType.RENDER,
            Instant.now(),
            summary,
            rawPayload,
            data,
            "render:" + eventType + ":" + eventId
        );
    }

    @Override
    public UUID saveArtifact(UUID projectId, NormalizedEvent event) {
        String wrappedContent = reliabilityService.wrapUntrustedData("RENDER", event.rawPayload());
        String title = "Render Deployment " + event.eventId().substring(0, Math.min(8, event.eventId().length()));

        CreateArtifactRequest request = new CreateArtifactRequest(
            title,
            "LOG",
            "RENDER",
            "deploy:" + event.eventId(),
            wrappedContent,
            event.summary()
        );

        ActorContext actor = ActorContext.system();
        ArtifactResponse response = artifactService.createArtifact(projectId, request, actor);

        timelineService.append(
            projectId,
            null,
            "RENDER",
            "RENDER_DEPLOYMENT",
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

    /**
     * Process and record a deployment from Render polling or direct sync.
     */
    public DeploymentRecord ingestDeployment(
        UUID projectId,
        String serviceId,
        String externalDeployId,
        String environment,
        String commitSha,
        String status,
        String rawLogs,
        Instant startedAt,
        Instant finishedAt
    ) {
        // Extract failure classification
        String failureClass = classifyFailure(status, rawLogs);
        List<String> extractedErrors = extractErrors(rawLogs);

        // Store logs as immutable artifact if logs exist
        UUID logArtifactId = null;
        if (rawLogs != null && !rawLogs.isBlank()) {
            String wrappedLogs = reliabilityService.wrapUntrustedData("RENDER_LOGS", rawLogs);
            CreateArtifactRequest logRequest = new CreateArtifactRequest(
                "Render Deploy Logs " + externalDeployId,
                "LOG",
                "RENDER",
                "deploy:" + externalDeployId,
                wrappedLogs,
                "Render deployment logs for " + externalDeployId
            );
            ArtifactResponse logArtifact = artifactService.createArtifact(projectId, logRequest, ActorContext.system());
            logArtifactId = logArtifact.id();
        }

        DeploymentRecord deployment = new DeploymentRecord(
            UUID.randomUUID(),
            projectId,
            ProviderType.RENDER,
            serviceId,
            externalDeployId,
            environment != null ? environment : "production",
            commitSha,
            status,
            failureClass,
            logArtifactId,
            null,
            extractedErrors,
            null,
            startedAt != null ? startedAt : Instant.now(),
            finishedAt,
            Instant.now()
        );

        DeploymentRecord saved = deploymentRepository.save(deployment);

        timelineService.append(
            projectId,
            null,
            "RENDER",
            "DEPLOYMENT_" + status.toUpperCase(),
            logArtifactId,
            null,
            1,
            "live".equalsIgnoreCase(status) ? "SUCCESS" : "FAILURE",
            null,
            "SYSTEM",
            "Render deploy [" + externalDeployId + "] status: " + status + (failureClass != null ? " (" + failureClass + ")" : ""),
            "{\"deploymentId\":\"" + saved.id() + "\",\"externalDeployId\":\"" + externalDeployId + "\"}"
        );

        return saved;
    }

    public String classifyFailure(String status, String logs) {
        if ("live".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status)) {
            return "NONE";
        }
        if (logs == null || logs.isBlank()) {
            if ("build_failed".equalsIgnoreCase(status)) return "BUILD_FAILURE";
            if ("deploy_failed".equalsIgnoreCase(status)) return "DEPLOY_FAILURE";
            if ("canceled".equalsIgnoreCase(status)) return "CANCELED";
            return "UNKNOWN";
        }

        if (BUILD_ERROR_PATTERN.matcher(logs).find() || "build_failed".equalsIgnoreCase(status)) {
            return "BUILD_FAILURE";
        }
        if (HEALTH_CHECK_PATTERN.matcher(logs).find() || "deploy_failed".equalsIgnoreCase(status)) {
            return "DEPLOY_FAILURE";
        }
        if (RUNTIME_CRASH_PATTERN.matcher(logs).find()) {
            return "RUNTIME_FAILURE";
        }

        return "UNKNOWN";
    }

    public List<String> extractErrors(String logs) {
        if (logs == null || logs.isBlank()) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        String[] lines = logs.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();
            if (trimmed.length() > 5 && (
                lower.contains("error") ||
                lower.contains("err") ||
                lower.contains("failed") ||
                lower.contains("exception") ||
                lower.contains("panic:") ||
                lower.contains("fatal")
            )) {
                errors.add(trimmed);
                if (errors.size() >= 10) break; // Limit extracted error highlights to top 10
            }
        }
        return errors;
    }

    @Override
    public List<NormalizedEvent> retrieve(UUID projectId, String cursor, int limit) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.RENDER);
        if (configOpt.isEmpty()) {
            log.info("Render integration not configured for project {}", projectId);
            return List.of();
        }

        IntegrationConfigRecord config = configOpt.get();
        Map<String, Object> configMeta = config.configMetadata();
        String serviceId = configMeta != null ? (String) configMeta.get("serviceId") : null;
        if (serviceId == null || serviceId.isBlank()) {
            log.warn("Render serviceId missing in configuration for project {}", projectId);
            return List.of();
        }

        var credOpt = credentialRepository.findByIntegrationId(config.id());
        if (credOpt.isEmpty()) {
            log.warn("Render credentials missing for project {}", projectId);
            return List.of();
        }

        CredentialEntity cred = credOpt.get();
        String apiKey;
        try {
            apiKey = encryptionService.decryptToString(cred.encryptedSecret(), cred.initializationVector(), cred.authTag(), cred.keyVersion());
        } catch (Exception e) {
            log.error("Failed to decrypt Render API key for project {}: {}", projectId, e.getMessage());
            return List.of();
        }

        int fetchLimit = limit > 0 ? Math.min(limit, 50) : 20;

        String uri = "/services/" + serviceId + "/deploys?limit=" + fetchLimit;
        if (cursor != null && !cursor.isBlank()) {
            uri += "&cursor=" + cursor;
        }

        List<NormalizedEvent> events = new ArrayList<>();
        String latestCursor = cursor;

        try {
            String responseBody = webClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));

            if (responseBody == null || responseBody.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    JsonNode deployNode = node.has("deploy") ? node.get("deploy") : node;
                    String itemCursor = node.has("cursor") ? node.get("cursor").asText() : null;

                    String deployId = deployNode.has("id") ? deployNode.get("id").asText() : null;
                    if (deployId == null) continue;

                    String status = deployNode.has("status") ? deployNode.get("status").asText() : "unknown";
                    String commitSha = null;
                    if (deployNode.has("commit")) {
                        JsonNode commitNode = deployNode.get("commit");
                        if (commitNode.has("id")) {
                            commitSha = commitNode.get("id").asText();
                        }
                    }

                    Instant createdAt = Instant.now();
                    if (deployNode.has("createdAt")) {
                        try {
                            createdAt = Instant.parse(deployNode.get("createdAt").asText());
                        } catch (Exception ignored) {}
                    }

                    Instant finishedAt = null;
                    if (deployNode.has("finishedAt") && !deployNode.get("finishedAt").isNull()) {
                        try {
                            finishedAt = Instant.parse(deployNode.get("finishedAt").asText());
                        } catch (Exception ignored) {}
                    }

                    // Ingest deployment
                    ingestDeployment(
                            projectId,
                            serviceId,
                            deployId,
                            "production",
                            commitSha,
                            status,
                            null,
                            createdAt,
                            finishedAt
                    );

                    NormalizedEvent event = new NormalizedEvent(
                            deployId,
                            "deploy",
                            ProviderType.RENDER,
                            createdAt,
                            "Render deploy [" + deployId + "]: " + status,
                            deployNode.toString(),
                            objectMapper.convertValue(deployNode, new TypeReference<Map<String, Object>>() {}),
                            "render:deploy:" + deployId
                    );
                    events.add(event);

                    if (itemCursor != null) {
                        latestCursor = itemCursor;
                    } else {
                        latestCursor = deployId;
                    }
                }
            }

            if (latestCursor != null) {
                checkpointRepository.saveCheckpoint(projectId, ProviderType.RENDER, "deploys", latestCursor);
            }

            configRepository.updateLastSyncAt(config.id(), Instant.now());

        } catch (WebClientResponseException.Unauthorized e) {
            configRepository.updateStatus(config.id(), IntegrationStatus.ERROR);
            timelineService.append(projectId, null, "RENDER", "POLL_FAILED_401", null, null, null, "ERROR", null, "SYSTEM", "Render API authentication failed (401)", "{}");
            log.error("Render API 401 Unauthorized for service {}", serviceId);
        } catch (WebClientResponseException.TooManyRequests e) {
            log.warn("Render rate limit hit (429) during polling for project {}", projectId);
        } catch (Exception e) {
            log.error("Failed to poll Render deploys for project {}: {}", projectId, e.getMessage());
        }

        return events;
    }

    @Override
    public IntegrationHealth syncStatus(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.RENDER);
        if (configOpt.isEmpty()) {
            return new IntegrationHealth(
                projectId,
                ProviderType.RENDER,
                IntegrationStatus.DISCONNECTED,
                null,
                Instant.now(),
                "Render not connected",
                Map.of()
            );
        }

        IntegrationConfigRecord config = configOpt.get();
        return new IntegrationHealth(
            projectId,
            ProviderType.RENDER,
            config.status(),
            config.lastSyncAt(),
            Instant.now(),
            "Render connected and polling configured",
            config.configMetadata()
        );
    }

    @Override
    public void handleError(UUID projectId, Exception error, String context) {
        log.error("Render Adapter Error [project={}, context={}]: {}", projectId, context, error.getMessage());
    }

    @Override
    public void disconnect(UUID projectId) {
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, ProviderType.RENDER);
        if (configOpt.isPresent()) {
            credentialRepository.deleteByIntegrationId(configOpt.get().id());
            configRepository.updateStatus(configOpt.get().id(), IntegrationStatus.DISCONNECTED);
        }
        timelineService.append(
            projectId,
            null,
            "RENDER",
            "INTEGRATION_DISCONNECTED",
            null,
            null,
            null,
            "SUCCESS",
            null,
            "SYSTEM",
            "Disconnected Render integration",
            "{}"
        );
    }
}
