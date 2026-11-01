package com.context_platform.aether.workflow.service;

import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.artifact.ArtifactVersion;
import com.context_platform.aether.decision.DecisionRepository;
import com.context_platform.aether.decision.TechnicalDecision;
import com.context_platform.aether.integration.model.DeploymentRecord;
import com.context_platform.aether.integration.repository.DeploymentRepository;
import com.context_platform.aether.integration.repository.IntegrationConfigRepository;
import com.context_platform.aether.storage.StorageService;
import com.context_platform.aether.workflow.model.DebuggingRecord;
import com.context_platform.aether.workflow.model.DebuggingStatus;
import com.context_platform.aether.workflow.model.FailureClassification;
import com.context_platform.aether.workflow.repository.DebuggingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DebuggingService {

    private final DebuggingRecordRepository debuggingRecordRepository;
    private final DeploymentRepository deploymentRepository;
    private final ArtifactRepository artifactRepository;
    private final StorageService storageService;
    private final DecisionRepository decisionRepository;
    private final IntegrationConfigRepository configRepository;

    private static final Pattern ERROR_PATTERN = Pattern.compile("(?i)(error|exception|fail|panic|fatal|crash).*");

    public DebuggingService(
            DebuggingRecordRepository debuggingRecordRepository,
            DeploymentRepository deploymentRepository,
            ArtifactRepository artifactRepository,
            StorageService storageService,
            DecisionRepository decisionRepository,
            IntegrationConfigRepository configRepository
    ) {
        this.debuggingRecordRepository = debuggingRecordRepository;
        this.deploymentRepository = deploymentRepository;
        this.artifactRepository = artifactRepository;
        this.storageService = storageService;
        this.decisionRepository = decisionRepository;
        this.configRepository = configRepository;
    }

    public Map<String, Object> assembleDebuggingPackage(
            UUID projectId,
            UUID taskId,
            UUID failedDeploymentId,
            FailureClassification classification
    ) {
        DeploymentRecord deploy = deploymentRepository.findById(failedDeploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + failedDeploymentId));

        // 1. Strict Deployment Scoping: Retrieve logs exclusively for failedDeploymentId
        String rawLogs = "";
        UUID logArtifactId = deploy.buildLogsArtifactId() != null ? deploy.buildLogsArtifactId() : deploy.runtimeLogsArtifactId();

        if (logArtifactId != null) {
            try {
                Optional<ArtifactVersion> verOpt = artifactRepository.getLatestVersion(logArtifactId, projectId);
                if (verOpt.isPresent()) {
                    var ver = verOpt.get();
                    byte[] bytes = storageService.retrieve(projectId, ver.storageKey(), ver.contentHash());
                    rawLogs = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}
        }

        // Untrusted data boundary wrapper
        String protectedLogs = "<!-- BEGIN_UNTRUSTED_DATA_BOUNDARY (DEPLOYMENT_ID: " + deploy.externalDeployId() + ") -->\n"
                + rawLogs
                + "\n<!-- END_UNTRUSTED_DATA_BOUNDARY -->";

        // Extract error lines
        List<String> extractedErrors = new ArrayList<>();
        if (!rawLogs.isBlank()) {
            for (String line : rawLogs.split("\n")) {
                if (ERROR_PATTERN.matcher(line).find() && extractedErrors.size() < 20) {
                    extractedErrors.add(line.trim());
                }
            }
        }
        if (extractedErrors.isEmpty() && deploy.extractedErrors() != null) {
            extractedErrors.addAll(deploy.extractedErrors());
        }

        // 2. Environment config names only (Rule 8: never include secret values)
        List<String> envConfigKeys = new ArrayList<>();
        var configs = configRepository.findByProjectId(projectId);
        for (var cfg : configs) {
            if (cfg.configMetadata() != null) {
                envConfigKeys.addAll(cfg.configMetadata().keySet());
            }
        }

        // 3. Database and connection decisions
        List<Map<String, Object>> dbDecisions = new ArrayList<>();
        List<TechnicalDecision> allDecisions = decisionRepository.listDecisions(projectId, null, null);
        for (var dec : allDecisions) {
            String titleLower = dec.title().toLowerCase();
            if (titleLower.contains("database") || titleLower.contains("postgres") || titleLower.contains("connection") || titleLower.contains("storage") || titleLower.contains("redis")) {
                dbDecisions.add(Map.of(
                        "id", dec.id(),
                        "title", dec.title(),
                        "category", dec.category(),
                        "status", dec.state()
                ));
            }
        }

        // 4. Prior debugging attempts for similar errors
        List<DebuggingRecord> priorAttempts = debuggingRecordRepository.findByClassification(projectId, classification);

        // 5. Record this new debugging package in repository
        DebuggingRecord newRecord = new DebuggingRecord(
                null,
                projectId,
                taskId,
                "DEPLOYMENT",
                classification,
                deploy.id(),
                null,
                null,
                "Initiated debugging package for failed deployment " + deploy.externalDeployId(),
                DebuggingStatus.INVESTIGATING,
                Instant.now(),
                null
        );
        DebuggingRecord saved = debuggingRecordRepository.save(newRecord);

        Map<String, Object> debugPackage = new LinkedHashMap<>();
        debugPackage.put("debuggingRecordId", saved.id());
        debugPackage.put("deploymentId", deploy.externalDeployId());
        debugPackage.put("environment", deploy.environment());
        debugPackage.put("commitSha", deploy.commitSha());
        debugPackage.put("classification", classification.name());
        debugPackage.put("timeWindow", Map.of(
                "startedAt", deploy.deployStartedAt() != null ? deploy.deployStartedAt().toString() : "unknown",
                "finishedAt", deploy.deployFinishedAt() != null ? deploy.deployFinishedAt().toString() : "unknown"
        ));
        debugPackage.put("extractedErrors", extractedErrors);
        debugPackage.put("isolatedLogs", protectedLogs);
        debugPackage.put("environmentConfigKeys", envConfigKeys);
        debugPackage.put("relevantDbDecisions", dbDecisions);
        debugPackage.put("priorDebuggingAttempts", priorAttempts);

        return debugPackage;
    }

    public void recordDebuggingAttempt(
            UUID projectId,
            UUID debuggingId,
            String rootCauseAnalysis,
            String attemptSummary,
            DebuggingStatus status
    ) {
        DebuggingRecord existing = debuggingRecordRepository.findById(projectId, debuggingId)
                .orElseThrow(() -> new IllegalArgumentException("Debugging record not found: " + debuggingId));

        DebuggingRecord updated = new DebuggingRecord(
                existing.id(),
                existing.projectId(),
                existing.taskId(),
                existing.failureType(),
                existing.classification(),
                existing.failedDeploymentId(),
                existing.resolvedDeploymentId(),
                rootCauseAnalysis != null ? rootCauseAnalysis : existing.rootCauseAnalysis(),
                attemptSummary != null ? attemptSummary : existing.attemptSummary(),
                status != null ? status : existing.status(),
                existing.createdAt(),
                status == DebuggingStatus.RESOLVED ? Instant.now() : existing.resolvedAt()
        );

        debuggingRecordRepository.save(updated);
    }

    @Transactional
    public void onDeploymentSuccess(UUID projectId, UUID successfulDeployId, String commitSha) {
        if (projectId == null || successfulDeployId == null) {
            return;
        }

        // 1. Fetch the successful deployment
        DeploymentRecord successDeploy = deploymentRepository.findById(successfulDeployId)
                .orElse(null);
        if (successDeploy == null) {
            return;
        }

        // 2. Ensure project matches strictly
        if (!projectId.equals(successDeploy.projectId())) {
            return;
        }

        String targetService = successDeploy.serviceId();
        String targetEnv = successDeploy.environment();

        // 3. Conservative matching: if serviceId or environment is null or blank, never resolve
        if (targetService == null || targetService.isBlank() || targetEnv == null || targetEnv.isBlank()) {
            return;
        }

        // 4. Find open debugging records whose failed deployment strictly matches serviceId and environment
        List<DeploymentRecord> recentDeploys = deploymentRepository.listByProject(projectId, 50);
        for (DeploymentRecord dep : recentDeploys) {
            // Must strictly match serviceId and environment
            if (dep.serviceId() == null || dep.environment() == null
                    || !targetService.equalsIgnoreCase(dep.serviceId())
                    || !targetEnv.equalsIgnoreCase(dep.environment())) {
                continue;
            }

            if ("FAILED".equalsIgnoreCase(dep.status()) || "ERROR".equalsIgnoreCase(dep.status())
                    || "deploy_failed".equalsIgnoreCase(dep.status()) || "build_failed".equalsIgnoreCase(dep.status())) {
                List<DebuggingRecord> records = debuggingRecordRepository.findByFailedDeploymentId(projectId, dep.id());
                for (DebuggingRecord rec : records) {
                    if (rec.status() != DebuggingStatus.RESOLVED) {
                        debuggingRecordRepository.markResolved(
                                projectId,
                                rec.id(),
                                successfulDeployId,
                                "Verified and resolved by successful subsequent deployment (" + successDeploy.externalDeployId()
                                        + ", commit " + (commitSha != null ? commitSha : successDeploy.commitSha())
                                        + ") on service " + targetService + " in " + targetEnv
                        );
                    }
                }
            }
        }
    }
}
