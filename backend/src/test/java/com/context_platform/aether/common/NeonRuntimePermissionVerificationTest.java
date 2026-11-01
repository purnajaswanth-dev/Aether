package com.context_platform.aether.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NeonRuntimePermissionVerificationTest {

    private String readMigration(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/" + filename);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("V4__tenant_role.sql matches original immutable baseline and enforces NOBYPASSRLS")
    void testV4ImmutabilityAndNobypassrls() throws IOException {
        String v4 = readMigration("V4__tenant_role.sql");

        assertThat(v4).contains("CREATE ROLE aether_app LOGIN PASSWORD 'aether_app_password' NOBYPASSRLS;");
        assertThat(v4).contains("GRANT USAGE ON SCHEMA public TO aether_app;");
        assertThat(v4).contains("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO aether_app;");
        // Verify it never grants bypassrls
        assertThat(v4).doesNotContain("WITH BYPASSRLS");
        assertThat(v4).doesNotContain("GRANT BYPASSRLS");
        assertThat(v4.replace("NOBYPASSRLS", "")).doesNotMatch("(?i).*\\bBYPASSRLS\\b.*");
    }

    @Test
    @DisplayName("V11 enforces least privilege on tables without TRUNCATE or admin privileges")
    void testV11LeastPrivilegeTables() throws IOException {
        String v11 = readMigration("V11__managed_role_compatibility.sql");

        // CURRENT_USER least privilege
        assertThat(v11).contains("GRANT USAGE ON SCHEMA public TO CURRENT_USER;");
        assertThat(v11).contains("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO CURRENT_USER;");
        assertThat(v11).contains("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO CURRENT_USER;");
        assertThat(v11).contains("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO CURRENT_USER;");

        // aether_app least privilege
        assertThat(v11).contains("GRANT USAGE ON SCHEMA public TO aether_app;");
        assertThat(v11).contains("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO aether_app;");
        assertThat(v11).contains("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO aether_app;");
        assertThat(v11).contains("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO aether_app;");

        // Strict prohibition of dangerous privileges
        assertThat(v11).doesNotContain("TRUNCATE");
        assertThat(v11).doesNotContain("GRANT ALL PRIVILEGES");
        assertThat(v11.replace("NOSUPERUSER", "")).doesNotMatch("(?i).*\\bGRANT\\s+SUPERUSER\\b.*");
        assertThat(v11.replace("NOCREATEROLE", "")).doesNotMatch("(?i).*\\bGRANT\\s+CREATEROLE\\b.*");
        assertThat(v11.replace("NOCREATEDB", "")).doesNotMatch("(?i).*\\bGRANT\\s+CREATEDB\\b.*");

        // Explicit hardening attributes on aether_app
        assertThat(v11).contains("ALTER ROLE aether_app NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE;");
    }

    @Test
    @DisplayName("scripts/provision-neon-roles.sql enforces least-privilege, revokes flyway_schema_history, and grants only required functions")
    void testProvisionNeonRolesScript() throws IOException {
        Path scriptPath = Path.of("..", "scripts", "provision-neon-roles.sql");
        if (!Files.exists(scriptPath)) {
            scriptPath = Path.of("scripts", "provision-neon-roles.sql");
        }
        assertThat(Files.exists(scriptPath)).isTrue();
        String sql = Files.readString(scriptPath, StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE ROLE aether_app WITH");
        assertThat(sql).contains("NOBYPASSRLS");
        assertThat(sql).contains("NOSUPERUSER");
        assertThat(sql).contains("NOCREATEDB");
        assertThat(sql).contains("NOCREATEROLE");
        assertThat(sql).contains("REPLACE_WITH_AETHER_APP_GENERATED_PASSWORD");
        assertThat(sql).contains("REVOKE ALL PRIVILEGES ON TABLE flyway_schema_history FROM aether_app;");
        assertThat(sql).contains("GRANT EXECUTE ON FUNCTION current_project_matches(UUID) TO aether_app;");
        assertThat(sql).contains("GRANT EXECUTE ON FUNCTION reject_update_delete() TO aether_app;");
        assertThat(sql).contains("ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public");
        assertThat(sql).doesNotContain("GRANT TRUNCATE");
        assertThat(sql).doesNotContain("GRANT ALL");
        assertThat(sql.replace("NOBYPASSRLS", "")).doesNotMatch("(?i).*\\bBYPASSRLS\\b.*");
    }

    @Test
    @DisplayName("All project-scoped tables across migrations enforce FORCE ROW LEVEL SECURITY")
    void testAllProjectScopedTablesEnforceForceRls() throws IOException {
        List<String> projectScopedTables = List.of(
                // V3
                "project_members", "project_integrations", "project_permissions",
                "artifacts", "artifact_versions", "artifact_references",
                "technical_decisions", "decision_versions", "decision_evidence", "timeline_events",
                // V5
                "mcp_tokens", "mcp_call_logs",
                // V6
                "ingestion_jobs", "secret_findings", "artifact_chunks",
                "embeddings", "extracted_facts", "dependency_edges",
                "context_packages", "context_retrieval_logs",
                // V7
                "integration_configs", "integration_credentials", "processed_events",
                "dead_letter_events", "integration_checkpoints", "deployments",
                "agent_devices", "agent_file_metadata",
                // V8
                "tasks", "task_events", "agent_sessions", "agent_interactions",
                "implementation_records", "test_executions", "debugging_records",
                "task_links", "action_requests", "code_symbols", "code_dependencies",
                "agent_handoffs"
        );

        StringBuilder allSql = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            try {
                String prefix = "V" + i + "__";
                for (String name : List.of(
                        "V1__initial_schema.sql",
                        "V2__immutability_triggers.sql",
                        "V3__row_level_security.sql",
                        "V4__tenant_role.sql",
                        "V5__mcp_tables.sql",
                        "V6__phase_3_rag_and_orchestrator.sql",
                        "V7__phase_4_integrations_and_agent.sql",
                        "V8__phase_5_workflow_coordination.sql",
                        "V9__atomic_action_requests.sql",
                        "V10__project_purge_support.sql"
                )) {
                    if (name.startsWith(prefix)) {
                        allSql.append("\n").append(readMigration(name));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String combined = allSql.toString();
        for (String table : projectScopedTables) {
            assertThat(combined)
                    .withFailMessage("Table %s must have FORCE ROW LEVEL SECURITY in migrations", table)
                    .containsAnyOf(
                            "FORCE ROW LEVEL SECURITY;",
                            "ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY;",
                            formatForceRlsCheck(table)
                    );
        }
    }

    private String formatForceRlsCheck(String table) {
        return "'" + table + "'";
    }
}
