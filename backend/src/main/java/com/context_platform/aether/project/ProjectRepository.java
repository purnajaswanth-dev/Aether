package com.context_platform.aether.project;

import com.context_platform.aether.security.RlsContextHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectRepository {

    private final JdbcClient jdbcClient;
    private final RlsContextHelper rlsContextHelper;
    private final ObjectMapper objectMapper;

    private static final List<String> CATALOGUE_PROVIDERS = List.of(
        "claude", "chatgpt", "antigravity", "github", "render", "postman", "docker"
    );

    private static final List<String> CONSTITUTIONAL_PERMISSIONS = List.of(
        "READ_PROJECT_CONTEXT",
        "READ_ARTIFACTS",
        "SAVE_ARTIFACTS",
        "RECORD_DECISIONS",
        "READ_DEPLOYMENT_DATA",
        "EXECUTE_APPROVED_ACTIONS"
    );

    public ProjectRepository(JdbcClient jdbcClient, RlsContextHelper rlsContextHelper, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.rlsContextHelper = rlsContextHelper;
        this.objectMapper = objectMapper;
    }

    public synchronized String nextPublicId() {
        Integer nextNum = jdbcClient.sql("""
            SELECT COALESCE(MAX(CAST(NULLIF(SUBSTRING(public_id FROM 5), '') AS INTEGER)), 0) + 1
            FROM projects
        """)
        .query(Integer.class)
        .single();
        return String.format("PRJ-%03d", nextNum != null ? nextNum : 1);
    }

    public Project createProject(
        String publicId,
        String name,
        String description,
        String projectType,
        List<String> techStack,
        String directory,
        String storagePreference
    ) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String techStackJson = serializeJson(techStack != null ? techStack : List.of());

        jdbcClient.sql("""
            INSERT INTO projects (id, public_id, name, description, project_type, tech_stack, directory, storage_preference, archived, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, false, now(), now())
        """)
        .param(id)
        .param(publicId)
        .param(name)
        .param(description)
        .param(projectType)
        .param(techStackJson)
        .param(directory)
        .param(storagePreference != null ? storagePreference : "LOCAL_FIRST")
        .update();

        return new Project(id, publicId, name, description, projectType, techStackJson, directory, storagePreference, false, now, now);
    }

    public void addMember(UUID projectId, UUID userId, String role) {
        rlsContextHelper.setProjectContext(projectId);
        jdbcClient.sql("""
            INSERT INTO project_members (id, project_id, user_id, role, created_at)
            VALUES (gen_random_uuid(), ?, ?, ?, now())
            ON CONFLICT (project_id, user_id) DO UPDATE SET role = EXCLUDED.role
        """)
        .param(projectId)
        .param(userId)
        .param(role)
        .update();
    }

    public Optional<String> getMemberRole(UUID projectId, UUID userId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT role FROM project_members WHERE project_id = ? AND user_id = ?
        """)
        .param(projectId)
        .param(userId)
        .query(String.class)
        .optional();
    }

    public void initializeCatalogueIntegrations(UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        for (String providerId : CATALOGUE_PROVIDERS) {
            UUID integrationId = UUID.randomUUID();
            jdbcClient.sql("""
                INSERT INTO project_integrations (id, project_id, provider_id, status, config, created_at, updated_at)
                VALUES (?, ?, ?, 'NOT_CONNECTED', '{}'::jsonb, now(), now())
                ON CONFLICT (project_id, provider_id) DO NOTHING
            """)
            .param(integrationId)
            .param(projectId)
            .param(providerId)
            .update();

            for (String perm : CONSTITUTIONAL_PERMISSIONS) {
                jdbcClient.sql("""
                    INSERT INTO project_permissions (id, project_id, integration_id, permission, granted, created_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, false, now())
                    ON CONFLICT (project_id, integration_id, permission) DO NOTHING
                """)
                .param(projectId)
                .param(integrationId)
                .param(perm)
                .update();
            }
        }
    }

    public Optional<Project> findById(UUID projectId) {
        return jdbcClient.sql("""
            SELECT id, public_id, name, description, project_type, tech_stack::text as tech_stack, directory, storage_preference, archived, created_at, updated_at
            FROM projects WHERE id = ?
        """)
        .param(projectId)
        .query(Project.class)
        .optional();
    }

    public Optional<Project> findByPublicId(String publicId) {
        return jdbcClient.sql("""
            SELECT id, public_id, name, description, project_type, tech_stack::text as tech_stack, directory, storage_preference, archived, created_at, updated_at
            FROM projects WHERE public_id = ?
        """)
        .param(publicId)
        .query(Project.class)
        .optional();
    }

    public List<ProjectResponse> listByUser(UUID userId) {
        List<Map<String, Object>> rows = jdbcClient.sql("""
            SELECT p.id, p.public_id, p.name, p.description, p.project_type, p.tech_stack::text as tech_stack,
                   p.directory, p.storage_preference, p.archived, p.created_at, p.updated_at, pm.role as user_role
            FROM projects p
            JOIN project_members pm ON p.id = pm.project_id
            WHERE pm.user_id = ? AND p.archived = false
            ORDER BY p.created_at DESC
        """)
        .param(userId)
        .query()
        .listOfRows();

        List<ProjectResponse> result = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String tsJson = (String) r.get("tech_stack");
            List<String> techStack = deserializeJsonList(tsJson);
            result.add(new ProjectResponse(
                (UUID) r.get("id"),
                (String) r.get("public_id"),
                (String) r.get("name"),
                (String) r.get("description"),
                (String) r.get("project_type"),
                techStack,
                (String) r.get("directory"),
                (String) r.get("storage_preference"),
                (Boolean) r.get("archived"),
                (String) r.get("user_role"),
                (Instant) r.get("created_at"),
                (Instant) r.get("updated_at")
            ));
        }
        return result;
    }

    public void updateProject(UUID projectId, String name, String description, String projectType, List<String> techStack, String directory, String storagePreference) {
        String techStackJson = serializeJson(techStack != null ? techStack : List.of());
        jdbcClient.sql("""
            UPDATE projects
            SET name = COALESCE(?, name),
                description = COALESCE(?, description),
                project_type = COALESCE(?, project_type),
                tech_stack = ?::jsonb,
                directory = COALESCE(?, directory),
                storage_preference = COALESCE(?, storage_preference),
                updated_at = now()
            WHERE id = ?
        """)
        .param(name)
        .param(description)
        .param(projectType)
        .param(techStackJson)
        .param(directory)
        .param(storagePreference)
        .param(projectId)
        .update();
    }

    public void archiveProject(UUID projectId) {
        jdbcClient.sql("UPDATE projects SET archived = true, updated_at = now() WHERE id = ?")
            .param(projectId)
            .update();
    }

    public List<Map<String, Object>> getPermissions(UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT pp.id, pp.permission, pp.granted, pi.provider_id
            FROM project_permissions pp
            JOIN project_integrations pi ON pp.integration_id = pi.id
            WHERE pp.project_id = ?
            ORDER BY pi.provider_id, pp.permission
        """)
        .param(projectId)
        .query()
        .listOfRows();
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> deserializeJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
