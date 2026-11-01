package com.context_platform.aether.integration.service;

import com.context_platform.aether.artifact.Artifact;
import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.artifact.ArtifactVersion;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.integration.adapter.IntegrationAdapter;
import com.context_platform.aether.integration.model.*;
import com.context_platform.aether.integration.registry.IntegrationRegistry;
import com.context_platform.aether.integration.reliability.IntegrationReliabilityService;
import com.context_platform.aether.integration.repository.*;
import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final IntegrationRegistry registry;
    private final IntegrationConfigRepository configRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final DeadLetterEventRepository deadLetterRepository;
    private final DeploymentRepository deploymentRepository;
    private final ArtifactRepository artifactRepository;
    private final StorageService storageService;
    private final IntegrationReliabilityService reliabilityService;
    private final ProjectSecurityChecker securityChecker;

    public IntegrationService(
        IntegrationRegistry registry,
        IntegrationConfigRepository configRepository,
        IntegrationCredentialRepository credentialRepository,
        DeadLetterEventRepository deadLetterRepository,
        DeploymentRepository deploymentRepository,
        ArtifactRepository artifactRepository,
        StorageService storageService,
        IntegrationReliabilityService reliabilityService,
        ProjectSecurityChecker securityChecker
    ) {
        this.registry = registry;
        this.configRepository = configRepository;
        this.credentialRepository = credentialRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.deploymentRepository = deploymentRepository;
        this.artifactRepository = artifactRepository;
        this.storageService = storageService;
        this.reliabilityService = reliabilityService;
        this.securityChecker = securityChecker;
    }

    public List<CapabilityDescriptor> listCapabilities() {
        return registry.getAllCapabilities();
    }

    public List<IntegrationSummaryResponse> listIntegrations(UUID projectId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        List<IntegrationConfigRecord> configs = configRepository.findByProjectId(projectId);
        Map<ProviderType, IntegrationConfigRecord> configMap = new EnumMap<>(ProviderType.class);
        for (IntegrationConfigRecord c : configs) {
            configMap.put(c.provider(), c);
        }

        List<IntegrationSummaryResponse> results = new ArrayList<>();
        for (CapabilityDescriptor cap : registry.getAllCapabilities()) {
            IntegrationConfigRecord conf = configMap.get(cap.provider());
            if (conf != null) {
                var credOpt = credentialRepository.findByIntegrationId(conf.id());
                String masked = credOpt.map(CredentialEntity::maskedPreview).orElse(null);
                results.add(new IntegrationSummaryResponse(
                    conf.id(),
                    conf.projectId(),
                    conf.provider(),
                    cap.displayName(),
                    conf.status(),
                    cap.primarySyncMethod(),
                    conf.configMetadata(),
                    masked,
                    conf.lastSyncAt(),
                    conf.createdAt()
                ));
            } else {
                results.add(new IntegrationSummaryResponse(
                    null,
                    projectId,
                    cap.provider(),
                    cap.displayName(),
                    IntegrationStatus.DISCONNECTED,
                    cap.primarySyncMethod(),
                    Map.of(),
                    null,
                    null,
                    null
                ));
            }
        }
        return results;
    }

    @Transactional
    public IntegrationHealth connectIntegration(UUID projectId, ConnectIntegrationRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");
        IntegrationAdapter adapter = registry.getAdapter(request.provider());
        return adapter.connect(projectId, request.config(), request.credential());
    }

    @Transactional
    public void disconnectIntegration(UUID projectId, ProviderType provider, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");
        IntegrationAdapter adapter = registry.getAdapter(provider);
        adapter.disconnect(projectId);
    }

    @Transactional
    public void pauseIntegration(UUID projectId, ProviderType provider, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, provider);
        if (configOpt.isPresent()) {
            configRepository.updateStatus(configOpt.get().id(), IntegrationStatus.PAUSED);
        }
    }

    @Transactional
    public void resumeIntegration(UUID projectId, ProviderType provider, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, provider);
        if (configOpt.isPresent()) {
            configRepository.updateStatus(configOpt.get().id(), IntegrationStatus.CONNECTED);
        }
    }

    @Transactional
    public IntegrationHealth triggerSync(UUID projectId, ProviderType provider, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        IntegrationAdapter adapter = registry.getAdapter(provider);
        adapter.retrieve(projectId, null, 50);
        var configOpt = configRepository.findByProjectIdAndProvider(projectId, provider);
        configOpt.ifPresent(c -> configRepository.updateLastSyncAt(c.id(), Instant.now()));
        return adapter.syncStatus(projectId);
    }

    public List<DeadLetterEventRecord> listDeadLetterEvents(UUID projectId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");
        return deadLetterRepository.listPending(projectId);
    }

    public void retryDeadLetterEvent(UUID projectId, UUID dlqId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");
        reliabilityService.retryDeadLetter(dlqId);
    }

    public void discardDeadLetterEvent(UUID projectId, UUID dlqId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");
        deadLetterRepository.markDiscarded(dlqId);
    }

    public List<DeploymentRecord> listDeployments(UUID projectId, int limit, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");
        return deploymentRepository.listByProject(projectId, limit);
    }

    public Optional<DeploymentRecord> getLatestDeployment(UUID projectId, String environment, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");
        return deploymentRepository.findLatest(projectId, environment);
    }

    public Optional<DeploymentRecord> getDeployment(UUID projectId, UUID deploymentId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");
        return deploymentRepository.findById(deploymentId);
    }

    public String getDeploymentLogs(UUID projectId, String externalDeployId, int tailLines, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");
        DeploymentRecord deploy = deploymentRepository.findByExternalDeployId(projectId, externalDeployId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Deployment not found: " + externalDeployId));

        if (deploy.buildLogsArtifactId() == null) {
            return "No logs recorded for deployment " + externalDeployId;
        }

        Optional<ArtifactVersion> versionOpt = artifactRepository.getLatestVersion(deploy.buildLogsArtifactId(), projectId);
        if (versionOpt.isEmpty()) {
            return "Log artifact version not found.";
        }

        ArtifactVersion version = versionOpt.get();
        String content;
        if (version.content() != null) {
            content = version.content();
        } else if (version.storageKey() != null) {
            byte[] bytes = storageService.retrieve(projectId, version.storageKey(), version.contentHash());
            content = new String(bytes, StandardCharsets.UTF_8);
        } else {
            content = "Log content empty.";
        }

        if (tailLines > 0) {
            String[] lines = content.split("\r?\n");
            if (lines.length > tailLines) {
                int start = lines.length - tailLines;
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                return sb.toString();
            }
        }
        return content;
    }
}
