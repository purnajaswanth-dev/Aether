package com.context_platform.aether.data;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.data.model.ProjectExportArchive;
import com.context_platform.aether.data.service.DataManagementService;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionResponse;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DataProtectionTest extends BaseTest {

    @Autowired
    private DataManagementService dataManagementService;

    private UUID projectId;
    private String projectPublicId;
    private UUID userId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("dataprot_" + UUID.randomUUID() + "@example.com", "Password123!", "Data Protection Tester");
        this.userId = user.user().id();
        this.userEmail = user.user().email();

        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest(
                "Data Prot Project " + System.currentTimeMillis(),
                "Testing data export and cascading deletion",
                "WEB_APP",
                List.of("Java"),
                "/dataprot/workspace",
                "LOCAL_FIRST"
            ),
            userId,
            userEmail
        );
        this.projectId = project.id();
        this.projectPublicId = project.publicId();

        // Create sample artifact
        artifactService.createArtifact(
            projectId,
            new CreateArtifactRequest(
                "SecurityPolicy.md",
                "SECURITY",
                "LOCAL",
                "sec-1",
                "# Security Policy\nStrict zero-trust authentication enforced.",
                "Initial security policy"
            ),
            ActorContext.human(userId, userEmail)
        );

        // Create sample decision
        decisionService.createDecision(
            projectId,
            new CreateDecisionRequest(
                "Enforce RS256 JWTs",
                "SECURITY",
                "We need cryptographic signing for inter-service communication.",
                "Enforce RSA-SHA256 signatures with 30-day rotation.",
                "Proven cryptographic strength.",
                "Requires key rotation infrastructure.",
                null
            ),
            ActorContext.human(userId, userEmail)
        );
    }

    @Test
    @DisplayName("Artifact Cryptographic Integrity: SHA-256 verification detects corrupted or tampered storage files")
    void testArtifactCryptographicIntegrity() {
        byte[] validContent = "Authentic unaltered engineering specification.".getBytes(StandardCharsets.UTF_8);
        var storageResult = storageService.store(projectId, "integrity-test.md", validContent);

        // 1. Retrieve with correct hash -> succeeds
        byte[] retrieved = storageService.retrieve(projectId, storageResult.storageKey(), storageResult.contentHash());
        assertThat(retrieved).isEqualTo(validContent);

        // 2. Retrieve with invalid/tampered expected hash -> fails with ApiException
        assertThatThrownBy(() -> storageService.retrieve(projectId, storageResult.storageKey(), "0000000000000000000000000000000000000000000000000000000000000000"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("tampering or corruption detected");
    }

    @Test
    @DisplayName("Project Data Export: Full archive contains project metadata, artifacts, decisions, and timeline")
    void testProjectDataExportArchive() {
        ProjectExportArchive archive = dataManagementService.exportProjectData(projectId, userId, userEmail);

        assertThat(archive).isNotNull();
        assertThat(archive.project().id()).isEqualTo(projectId);
        assertThat(archive.artifacts()).hasSize(1);
        assertThat(archive.artifacts().get(0).artifact().title()).isEqualTo("SecurityPolicy.md");
        assertThat(archive.artifacts().get(0).versions()).isNotEmpty();
        assertThat(archive.decisions()).hasSize(1);
        assertThat(archive.decisions().get(0).decision().title()).isEqualTo("Enforce RS256 JWTs");
        assertThat(archive.timelineEvents()).isNotEmpty();
        assertThat(archive.exportedBy()).isEqualTo(userEmail);
    }

    @Test
    @DisplayName("Cascading Deletion: Confirmation verification and complete purge of project and dependent entities")
    void testCascadingDeletionWithConfirmation() {
        // 1. Attempt deletion with wrong confirmation code -> rejected
        assertThatThrownBy(() -> dataManagementService.deleteProjectData(projectId, userId, "WRONG_CODE"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Invalid confirmation code");

        // 2. Perform deletion with exact confirmation code
        dataManagementService.deleteProjectData(projectId, userId, "CONFIRM_DELETE_" + projectPublicId);

        // 3. Verify project row is deleted
        Integer projectCount = jdbcClient.sql("SELECT COUNT(*) FROM projects WHERE id = ?")
            .param(projectId)
            .query(Integer.class)
            .single();
        assertThat(projectCount).isEqualTo(0);

        // 4. Verify cascading deletion purged artifacts, versions, decisions, timeline
        Integer artifactCount = jdbcClient.sql("SELECT COUNT(*) FROM artifacts WHERE project_id = ?")
            .param(projectId)
            .query(Integer.class)
            .single();
        assertThat(artifactCount).isEqualTo(0);

        Integer decisionCount = jdbcClient.sql("SELECT COUNT(*) FROM technical_decisions WHERE project_id = ?")
            .param(projectId)
            .query(Integer.class)
            .single();
        assertThat(decisionCount).isEqualTo(0);

        Integer timelineCount = jdbcClient.sql("SELECT COUNT(*) FROM timeline_events WHERE project_id = ?")
            .param(projectId)
            .query(Integer.class)
            .single();
        assertThat(timelineCount).isEqualTo(0);
    }
}
