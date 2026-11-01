package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.auth.RegisterRequest;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RowLevelSecurityIsolationTest extends BaseTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Gate 3: Cross-project isolation enforced under RLS and API service layer (User A cannot access User B's project)")
    void testCrossProjectIsolation() throws Exception {
        rlsContextHelper.setBypassRls(true);

        // 1. Create User A and Project A
        AuthResponse userA = authService.register(
            new RegisterRequest("user_a_" + UUID.randomUUID() + "@team.com", "Password123!", "User Alpha"),
            "127.0.0.1", "JUnit"
        );
        ProjectResponse projectA = projectService.createProject(
            new CreateProjectRequest("Project Alpha", "Project A details", "Monolith", List.of("Java"), null, "LOCAL_FIRST"),
            userA.user().id(),
            userA.user().email()
        );

        // 2. Create User B and Project B
        AuthResponse userB = authService.register(
            new RegisterRequest("user_b_" + UUID.randomUUID() + "@team.com", "Password123!", "User Beta"),
            "127.0.0.1", "JUnit"
        );
        ProjectResponse projectB = projectService.createProject(
            new CreateProjectRequest("Project Beta", "Project B details", "Microservices", List.of("Go"), null, "LOCAL_FIRST"),
            userB.user().id(),
            userB.user().email()
        );

        // 3. User A creates an artifact in Project A
        ArtifactResponse artifactA = artifactService.createArtifact(
            projectA.id(),
            new CreateArtifactRequest("Project A Secret Arch", "ARCHITECTURE", "MANUAL", null, "Secret Arch A", "v1"),
            userA.user().id()
        );

        // 4. User B attempts to access Project A via Service / API -> Must throw ProjectAccessDeniedException
        assertThatThrownBy(() -> {
            projectService.getProject(projectA.id(), userB.user().id());
        }).isInstanceOf(ProjectAccessDeniedException.class);

        assertThatThrownBy(() -> {
            artifactService.getLatest(projectA.id(), artifactA.id(), userB.user().id());
        }).isInstanceOf(ProjectAccessDeniedException.class);

        // 5. Test direct PostgreSQL Row-Level Security (RLS) enforcement under aether_app
        // Using a dedicated connection with an explicit transaction ensures SET LOCAL settings
        // and session ROLE persist across queries without connection-pool crosstalk.
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET ROLE aether_app");
                stmt.execute("SET LOCAL app.bypass_rls = 'off'");
                stmt.execute("SET LOCAL app.current_project_id = '" + projectB.id() + "'");

                // Cross-project query: under Project B context, Project A's artifact is invisible
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM artifacts WHERE project_id = ?")) {
                    ps.setObject(1, projectA.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isFalse(); // Zero rows visible under RLS!
                    }
                }

                // Cross-project UPDATE: under Project B context, updating Project A's record affects 0 rows
                try (PreparedStatement ps = conn.prepareStatement("UPDATE artifacts SET title = 'Compromised' WHERE id = ? AND project_id = ?")) {
                    ps.setObject(1, artifactA.id());
                    ps.setObject(2, projectA.id());
                    int updated = ps.executeUpdate();
                    assertThat(updated).isEqualTo(0);
                }

                // Cross-project DELETE: under Project B context, deleting Project A's record affects 0 rows
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM artifacts WHERE id = ? AND project_id = ?")) {
                    ps.setObject(1, artifactA.id());
                    ps.setObject(2, projectA.id());
                    int deleted = ps.executeUpdate();
                    assertThat(deleted).isEqualTo(0);
                }
            }
            conn.rollback();

            // Verification: Switch context to Project A -> Project A can access its own records
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET ROLE aether_app");
                stmt.execute("SET LOCAL app.bypass_rls = 'off'");
                stmt.execute("SET LOCAL app.current_project_id = '" + projectA.id() + "'");

                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM artifacts WHERE project_id = ?")) {
                    ps.setObject(1, projectA.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat((UUID) rs.getObject("id")).isEqualTo(artifactA.id());
                    }
                }
            }
            conn.rollback();

            // Cross-project INSERT: attempting to insert record for Project A while under Project B context violates RLS
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET ROLE aether_app");
                stmt.execute("SET LOCAL app.bypass_rls = 'off'");
                stmt.execute("SET LOCAL app.current_project_id = '" + projectB.id() + "'");

                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO artifacts (id, project_id, title, source_platform, artifact_type, created_at)
                    VALUES (gen_random_uuid(), ?, 'Sneaky Insertion', 'MANUAL', 'ARCHITECTURE', now())
                """)) {
                    ps.setObject(1, projectA.id());
                    assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(Exception.class);
                }
            }
            conn.rollback();
        }
    }
}
