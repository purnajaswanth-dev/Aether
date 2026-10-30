package com.context_platform.aether.artifact;

import com.context_platform.aether.security.RlsContextHelper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ArtifactRepository {

    private final JdbcClient jdbcClient;
    private final RlsContextHelper rlsContextHelper;

    public ArtifactRepository(JdbcClient jdbcClient, RlsContextHelper rlsContextHelper) {
        this.jdbcClient = jdbcClient;
        this.rlsContextHelper = rlsContextHelper;
    }

    public Artifact createArtifact(
        UUID projectId,
        String title,
        String sourcePlatform,
        String sourceReference,
        String artifactType,
        UUID relatedTaskId
    ) {
        rlsContextHelper.setProjectContext(projectId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcClient.sql("""
            INSERT INTO artifacts (id, project_id, title, source_platform, source_reference, artifact_type, related_task_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
        """)
        .param(id)
        .param(projectId)
        .param(title)
        .param(sourcePlatform)
        .param(sourceReference)
        .param(artifactType)
        .param(relatedTaskId)
        .update();

        return new Artifact(id, projectId, title, sourcePlatform, sourceReference, artifactType, relatedTaskId, now);
    }

    public ArtifactVersion appendVersion(
        UUID artifactId,
        UUID projectId,
        int version,
        String content,
        String storageKey,
        String contentHash,
        long byteSize,
        String createdBy,
        String actorType,
        String changeSummary
    ) {
        rlsContextHelper.setProjectContext(projectId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcClient.sql("""
            INSERT INTO artifact_versions (
                id, artifact_id, project_id, version, content, storage_key,
                content_hash, byte_size, created_by, actor_type, change_summary, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        """)
        .param(id)
        .param(artifactId)
        .param(projectId)
        .param(version)
        .param(content)
        .param(storageKey)
        .param(contentHash)
        .param(byteSize)
        .param(createdBy)
        .param(actorType != null ? actorType : "HUMAN_USER")
        .param(changeSummary)
        .update();

        return new ArtifactVersion(
            id, artifactId, projectId, version, content, storageKey,
            contentHash, byteSize, createdBy, actorType, changeSummary, now
        );
    }

    public Optional<Artifact> findArtifactById(UUID artifactId, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, project_id, title, source_platform, source_reference, artifact_type, related_task_id, created_at
            FROM artifacts WHERE id = ? AND project_id = ?
        """)
        .param(artifactId)
        .param(projectId)
        .query(Artifact.class)
        .optional();
    }

    public List<Artifact> listArtifacts(UUID projectId, String artifactType, String sourcePlatform, String search) {
        rlsContextHelper.setProjectContext(projectId);
        var sql = new StringBuilder("""
            SELECT id, project_id, title, source_platform, source_reference, artifact_type, related_task_id, created_at
            FROM artifacts
            WHERE project_id = ?
        """);

        if (artifactType != null && !artifactType.isBlank()) {
            sql.append(" AND artifact_type = '").append(artifactType).append("'");
        }
        if (sourcePlatform != null && !sourcePlatform.isBlank()) {
            sql.append(" AND source_platform = '").append(sourcePlatform).append("'");
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND lower(title) LIKE '%").append(search.toLowerCase().replace("'", "''")).append("%'");
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbcClient.sql(sql.toString())
            .param(projectId)
            .query(Artifact.class)
            .list();
    }

    public Optional<ArtifactVersion> getLatestVersion(UUID artifactId, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, artifact_id, project_id, version, content, storage_key,
                   content_hash, byte_size, created_by, actor_type, change_summary, created_at
            FROM artifact_versions
            WHERE artifact_id = ? AND project_id = ?
            ORDER BY version DESC LIMIT 1
        """)
        .param(artifactId)
        .param(projectId)
        .query(ArtifactVersion.class)
        .optional();
    }

    public Optional<ArtifactVersion> getVersion(UUID artifactId, int version, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, artifact_id, project_id, version, content, storage_key,
                   content_hash, byte_size, created_by, actor_type, change_summary, created_at
            FROM artifact_versions
            WHERE artifact_id = ? AND version = ? AND project_id = ?
        """)
        .param(artifactId)
        .param(version)
        .param(projectId)
        .query(ArtifactVersion.class)
        .optional();
    }

    public List<ArtifactVersion> listVersions(UUID artifactId, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, artifact_id, project_id, version, content, storage_key,
                   content_hash, byte_size, created_by, actor_type, change_summary, created_at
            FROM artifact_versions
            WHERE artifact_id = ? AND project_id = ?
            ORDER BY version DESC
        """)
        .param(artifactId)
        .param(projectId)
        .query(ArtifactVersion.class)
        .list();
    }
}
