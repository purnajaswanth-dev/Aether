package com.context_platform.aether.ingestion.service;

import com.context_platform.aether.ingestion.model.IngestionJob;
import com.context_platform.aether.ingestion.model.IngestionStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IngestionJobRepository {

    private final JdbcClient jdbcClient;

    public IngestionJobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(IngestionJob job) {
        jdbcClient.sql("""
            INSERT INTO ingestion_jobs (id, project_id, artifact_id, artifact_version, status, attempts, max_attempts, last_error, step_details, created_at, started_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                attempts = EXCLUDED.attempts,
                last_error = EXCLUDED.last_error,
                step_details = EXCLUDED.step_details,
                started_at = EXCLUDED.started_at,
                completed_at = EXCLUDED.completed_at
            """)
            .params(
                job.id(),
                job.projectId(),
                job.artifactId(),
                job.artifactVersion(),
                job.status().name(),
                job.attempts(),
                job.maxAttempts(),
                job.lastError(),
                job.stepDetails() != null ? job.stepDetails() : "{}",
                Timestamp.from(job.createdAt()),
                job.startedAt() != null ? Timestamp.from(job.startedAt()) : null,
                job.completedAt() != null ? Timestamp.from(job.completedAt()) : null
            )
            .update();
    }

    public Optional<IngestionJob> findById(UUID id) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, status, attempts, max_attempts, last_error, step_details::text, created_at, started_at, completed_at
            FROM ingestion_jobs
            WHERE id = ?
            """)
            .param(id)
            .query((rs, rowNum) -> new IngestionJob(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                IngestionStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("last_error"),
                rs.getString("step_details"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
            ))
            .optional();
    }

    public Optional<IngestionJob> findByArtifact(UUID projectId, UUID artifactId, int version) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, status, attempts, max_attempts, last_error, step_details::text, created_at, started_at, completed_at
            FROM ingestion_jobs
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            ORDER BY created_at DESC
            LIMIT 1
            """)
            .params(projectId, artifactId, version)
            .query((rs, rowNum) -> new IngestionJob(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                IngestionStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("last_error"),
                rs.getString("step_details"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
            ))
            .optional();
    }

    public List<IngestionJob> listFailedOrNeedsAttention(UUID projectId) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, status, attempts, max_attempts, last_error, step_details::text, created_at, started_at, completed_at
            FROM ingestion_jobs
            WHERE project_id = ? AND status IN ('FAILED', 'NEEDS_ATTENTION')
            ORDER BY created_at DESC
            """)
            .param(projectId)
            .query((rs, rowNum) -> new IngestionJob(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                IngestionStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("last_error"),
                rs.getString("step_details"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
            ))
            .list();
    }

    public List<IngestionJob> listRecent(UUID projectId, int limit) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, status, attempts, max_attempts, last_error, step_details::text, created_at, started_at, completed_at
            FROM ingestion_jobs
            WHERE project_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """)
            .params(projectId, limit)
            .query((rs, rowNum) -> new IngestionJob(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                IngestionStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getString("last_error"),
                rs.getString("step_details"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null
            ))
            .list();
    }

    public void deleteByArtifactVersion(UUID projectId, UUID artifactId, int version) {
        jdbcClient.sql("""
            DELETE FROM ingestion_jobs
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            """)
            .params(projectId, artifactId, version)
            .update();
    }
}

