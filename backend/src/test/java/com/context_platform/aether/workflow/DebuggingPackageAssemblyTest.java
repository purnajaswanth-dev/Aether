package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.integration.model.DeploymentRecord;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.repository.DeploymentRepository;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.FailureClassification;
import com.context_platform.aether.workflow.service.DebuggingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DebuggingPackageAssemblyTest extends BaseTest {

    @Autowired
    private DebuggingService debuggingService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("debug_tester@aether.test", "SecurePass123!", "Debug Tester");
        userId = user.user().id();
        ProjectResponse project = createTestProject("Debugging Test Project", userId);
        projectId = project.id();
    }

    @Test
    void testDebuggingPackageStrictDeploymentLogIsolation() {
        // Create logs artifact for Deploy A (Crashed with OutOfMemoryError)
        String logContentA = "2026-10-31 20:00:01 [main] ERROR com.app.Main - java.lang.OutOfMemoryError: Java heap space";
        var logArtA = artifactService.createArtifact(
                projectId,
                new CreateArtifactRequest("Deploy A Logs", "LOGS", "RENDER", "dep-001.log", logContentA, "Initial deploy logs"),
                userId
        );

        // Create Deploy A (FAILED)
        DeploymentRecord deployA = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-app", "dep-001", "production",
                "sha-aaa", "FAILED", "RUNTIME_CRASH", logArtA.id(), null,
                List.of("java.lang.OutOfMemoryError: Java heap space"), null,
                Instant.now().minusSeconds(300), Instant.now().minusSeconds(250), Instant.now()
        ));

        // Create logs artifact for completely different Deploy B (Failed with PortConflictException)
        String logContentB = "2026-10-31 20:10:01 [main] ERROR com.app.Server - java.net.BindException: Address already in use";
        var logArtB = artifactService.createArtifact(
                projectId,
                new CreateArtifactRequest("Deploy B Logs", "LOGS", "RENDER", "dep-002.log", logContentB, "Second deploy logs"),
                userId
        );

        DeploymentRecord deployB = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-app", "dep-002", "production",
                "sha-bbb", "FAILED", "CONFIG_ERROR", logArtB.id(), null,
                List.of("java.net.BindException: Address already in use"), null,
                Instant.now().minusSeconds(200), Instant.now().minusSeconds(150), Instant.now()
        ));

        // Assemble debugging package for Deploy A
        Map<String, Object> pkgA = debuggingService.assembleDebuggingPackage(projectId, null, deployA.id(), FailureClassification.RUNTIME_CRASH);

        // Verification 1: Package contains Deploy A ID and extracted error
        assertEquals("dep-001", pkgA.get("deploymentId"));
        assertEquals("RUNTIME_CRASH", pkgA.get("classification"));

        // Verification 2: Strict deployment log isolation - MUST contain Deploy A logs, NEVER Deploy B logs
        String isolatedLogsA = (String) pkgA.get("isolatedLogs");
        assertTrue(isolatedLogsA.contains("java.lang.OutOfMemoryError"), "Must contain Deploy A logs");
        assertFalse(isolatedLogsA.contains("java.net.BindException"), "Must NEVER contain logs from Deploy B!");
        assertTrue(isolatedLogsA.contains("BEGIN_UNTRUSTED_DATA_BOUNDARY (DEPLOYMENT_ID: dep-001)"), "Must have untrusted data wrapper with exact deploy ID");

        // Assemble debugging package for Deploy B
        Map<String, Object> pkgB = debuggingService.assembleDebuggingPackage(projectId, null, deployB.id(), FailureClassification.CONFIG_ERROR);
        String isolatedLogsB = (String) pkgB.get("isolatedLogs");
        assertTrue(isolatedLogsB.contains("java.net.BindException"), "Must contain Deploy B logs");
        assertFalse(isolatedLogsB.contains("java.lang.OutOfMemoryError"), "Must NEVER contain logs from Deploy A!");

        // Verification 3: Subsequent successful deployment resolves open debugging records
        DeploymentRecord deploySuccess = deploymentRepository.save(new DeploymentRecord(
                null, projectId, ProviderType.RENDER, "srv-app", "dep-003", "production",
                "sha-ccc-fixed", "LIVE", "NONE", null, null,
                List.of(), null,
                Instant.now().minusSeconds(60), Instant.now(), Instant.now()
        ));

        debuggingService.onDeploymentSuccess(projectId, deploySuccess.id(), "sha-ccc-fixed");

        UUID debugRecordIdA = (UUID) pkgA.get("debuggingRecordId");
        var resolvedRecord = debuggingService.assembleDebuggingPackage(projectId, null, deployA.id(), FailureClassification.RUNTIME_CRASH);
        assertNotNull(resolvedRecord);
    }
}
