package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.auth.RegisterRequest;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TriggerImmutabilityTest extends BaseTest {

    @Test
    @DisplayName("Gate 2: Direct SQL UPDATE/DELETE on artifact_versions and timeline_events fails via database triggers")
    void testImmutableDatabaseTriggers() {
        rlsContextHelper.setBypassRls(true);

        // 1. Setup user and project
        String email = "immutability_" + UUID.randomUUID() + "@team.com";
        AuthResponse user = authService.register(new RegisterRequest(email, "SecretPassword123!", "Immutability Tester"), "127.0.0.1", "JUnit");
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Immutable Test Project", "Testing triggers", "Web App", List.of("Java"), null, "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        // 2. Create artifact
        ArtifactResponse artifact = artifactService.createArtifact(
            project.id(),
            new CreateArtifactRequest("Arch Doc v1", "ARCHITECTURE", "MANUAL", null, "Immutable content original", "v1"),
            user.user().id()
        );

        // 3. Attempt direct SQL UPDATE on artifact_versions -> must fail via trigger
        assertThatThrownBy(() -> {
            jdbcClient.sql("UPDATE artifact_versions SET content = 'hacked' WHERE artifact_id = ?")
                .param(artifact.id())
                .update();
        })
        .isInstanceOf(Exception.class)
        .hasMessageContaining("strictly forbidden");

        // 4. Attempt direct SQL DELETE on artifact_versions -> must fail via trigger
        assertThatThrownBy(() -> {
            jdbcClient.sql("DELETE FROM artifact_versions WHERE artifact_id = ?")
                .param(artifact.id())
                .update();
        })
        .isInstanceOf(Exception.class)
        .hasMessageContaining("strictly forbidden");

        // 5. Attempt direct SQL UPDATE on timeline_events -> must fail via trigger
        assertThatThrownBy(() -> {
            jdbcClient.sql("UPDATE timeline_events SET summary = 'altered' WHERE project_id = ?")
                .param(project.id())
                .update();
        })
        .isInstanceOf(Exception.class)
        .hasMessageContaining("strictly forbidden");

        // 6. Attempt direct SQL DELETE on timeline_events -> must fail via trigger
        assertThatThrownBy(() -> {
            jdbcClient.sql("DELETE FROM timeline_events WHERE project_id = ?")
                .param(project.id())
                .update();
        })
        .isInstanceOf(Exception.class)
        .hasMessageContaining("strictly forbidden");

        // Verify original content is intact
        ArtifactResponse latest = artifactService.getLatest(project.id(), artifact.id(), user.user().id());
        assertThat(latest.content()).isEqualTo("Immutable content original");
    }
}
