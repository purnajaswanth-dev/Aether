package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.DebuggingRecord;
import com.context_platform.aether.workflow.model.DebuggingStatus;
import com.context_platform.aether.workflow.model.FailureClassification;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DebuggingRecordRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<DebuggingRecord> rowMapper;

    public DebuggingRecordRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = (rs, rowNum) -> new DebuggingRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getString("failure_type"),
                FailureClassification.valueOf(rs.getString("classification")),
                rs.getObject("failed_deployment_id", UUID.class),
                rs.getObject("resolved_deployment_id", UUID.class),
                rs.getString("root_cause_analysis"),
                rs.getString("attempt_summary"),
                DebuggingStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
                rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null
        );
    }

    public DebuggingRecord save(DebuggingRecord record) {
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO debugging_records (id, project_id, task_id, failure_type, classification, failed_deployment_id, resolved_deployment_id, root_cause_analysis, attempt_summary, status, created_at, resolved_at)
                VALUES (:id, :projectId, :taskId, :failureType, :classification, :failedDeploymentId, :resolvedDeploymentId, :rootCauseAnalysis, :attemptSummary, :status, NOW(), :resolvedAt)
                ON CONFLICT (id) DO UPDATE
                SET status = EXCLUDED.status,
                    root_cause_analysis = EXCLUDED.root_cause_analysis,
                    attempt_summary = EXCLUDED.attempt_summary,
                    resolved_deployment_id = EXCLUDED.resolved_deployment_id,
                    resolved_at = EXCLUDED.resolved_at
                """)
                .param("id", id)
                .param("projectId", record.projectId())
                .param("taskId", record.taskId())
                .param("failureType", record.failureType())
                .param("classification", record.classification().name())
                .param("failedDeploymentId", record.failedDeploymentId())
                .param("resolvedDeploymentId", record.resolvedDeploymentId())
                .param("rootCauseAnalysis", record.rootCauseAnalysis())
                .param("attemptSummary", record.attemptSummary())
                .param("status", record.status().name())
                .param("resolvedAt", record.resolvedAt() != null ? Timestamp.from(record.resolvedAt()) : null)
                .update();

        return findById(record.projectId(), id).orElseThrow();
    }

    public Optional<DebuggingRecord> findById(UUID projectId, UUID id) {
        return jdbcClient.sql("""
                SELECT * FROM debugging_records WHERE project_id = :projectId AND id = :id
                """)
                .param("projectId", projectId)
                .param("id", id)
                .query(rowMapper)
                .optional();
    }

    public List<DebuggingRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM debugging_records
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at DESC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }

    public List<DebuggingRecord> findByFailedDeploymentId(UUID projectId, UUID deploymentId) {
        return jdbcClient.sql("""
                SELECT * FROM debugging_records
                WHERE project_id = :projectId AND failed_deployment_id = :deploymentId
                ORDER BY created_at DESC
                """)
                .param("projectId", projectId)
                .param("deploymentId", deploymentId)
                .query(rowMapper)
                .list();
    }

    public List<DebuggingRecord> findByClassification(UUID projectId, FailureClassification classification) {
        return jdbcClient.sql("""
                SELECT * FROM debugging_records
                WHERE project_id = :projectId AND classification = :classification
                ORDER BY created_at DESC
                LIMIT 10
                """)
                .param("projectId", projectId)
                .param("classification", classification.name())
                .query(rowMapper)
                .list();
    }

    public void markResolved(UUID projectId, UUID debuggingId, UUID resolvedDeploymentId, String rootCauseAnalysis) {
        jdbcClient.sql("""
                UPDATE debugging_records
                SET status = 'RESOLVED',
                    resolved_deployment_id = :resolvedDeploymentId,
                    root_cause_analysis = :rootCauseAnalysis,
                    resolved_at = NOW()
                WHERE project_id = :projectId AND id = :debuggingId
                """)
                .param("projectId", projectId)
                .param("debuggingId", debuggingId)
                .param("resolvedDeploymentId", resolvedDeploymentId)
                .param("rootCauseAnalysis", rootCauseAnalysis)
                .update();
    }
}

