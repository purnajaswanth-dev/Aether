package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.DeploymentRecord;
import com.context_platform.aether.integration.model.ProviderType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeploymentRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<DeploymentRecord> rowMapper;

    public DeploymentRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            List<String> extractedErrors = List.of();
            String json = rs.getString("extracted_errors");
            if (json != null && !json.isBlank()) {
                try {
                    extractedErrors = objectMapper.readValue(json, new TypeReference<List<String>>() {});
                } catch (JsonProcessingException ignored) {}
            }

            Timestamp startTs = rs.getTimestamp("deploy_started_at");
            Timestamp finishTs = rs.getTimestamp("deploy_finished_at");

            return new DeploymentRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                ProviderType.valueOf(rs.getString("provider")),
                rs.getString("service_id"),
                rs.getString("external_deploy_id"),
                rs.getString("environment"),
                rs.getString("commit_sha"),
                rs.getString("status"),
                rs.getString("failure_classification"),
                rs.getObject("build_logs_artifact_id", UUID.class),
                rs.getObject("runtime_logs_artifact_id", UUID.class),
                extractedErrors,
                rs.getObject("linked_task_id", UUID.class),
                startTs != null ? startTs.toInstant() : null,
                finishTs != null ? finishTs.toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
            );
        };
    }

    public DeploymentRecord save(DeploymentRecord record) {
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();
        String errorsJson;
        try {
            errorsJson = objectMapper.writeValueAsString(record.extractedErrors() != null ? record.extractedErrors() : List.of());
        } catch (JsonProcessingException e) {
            errorsJson = "[]";
        }

        jdbcClient.sql("""
            INSERT INTO deployments (
                id, project_id, provider, service_id, external_deploy_id, environment,
                commit_sha, status, failure_classification, build_logs_artifact_id,
                runtime_logs_artifact_id, extracted_errors, linked_task_id,
                deploy_started_at, deploy_finished_at, created_at
            )
            VALUES (
                :id, :projectId, :provider, :serviceId, :externalDeployId, :environment,
                :commitSha, :status, :failureClassification, :buildLogsArtifactId,
                :runtimeLogsArtifactId, :extractedErrors::jsonb, :linkedTaskId,
                :deployStartedAt, :deployFinishedAt, now()
            )
            ON CONFLICT (project_id, provider, external_deploy_id) DO UPDATE SET
                status = EXCLUDED.status,
                failure_classification = EXCLUDED.failure_classification,
                build_logs_artifact_id = EXCLUDED.build_logs_artifact_id,
                runtime_logs_artifact_id = EXCLUDED.runtime_logs_artifact_id,
                extracted_errors = EXCLUDED.extracted_errors,
                linked_task_id = EXCLUDED.linked_task_id,
                deploy_started_at = EXCLUDED.deploy_started_at,
                deploy_finished_at = EXCLUDED.deploy_finished_at
            """)
            .param("id", id)
            .param("projectId", record.projectId())
            .param("provider", record.provider().name())
            .param("serviceId", record.serviceId())
            .param("externalDeployId", record.externalDeployId())
            .param("environment", record.environment() != null ? record.environment() : "production")
            .param("commitSha", record.commitSha())
            .param("status", record.status())
            .param("failureClassification", record.failureClassification())
            .param("buildLogsArtifactId", record.buildLogsArtifactId())
            .param("runtimeLogsArtifactId", record.runtimeLogsArtifactId())
            .param("extractedErrors", errorsJson)
            .param("linkedTaskId", record.linkedTaskId())
            .param("deployStartedAt", record.deployStartedAt() != null ? Timestamp.from(record.deployStartedAt()) : null)
            .param("deployFinishedAt", record.deployFinishedAt() != null ? Timestamp.from(record.deployFinishedAt()) : null)
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<DeploymentRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM deployments WHERE id = :id")
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public Optional<DeploymentRecord> findByExternalDeployId(UUID projectId, String externalDeployId) {
        return jdbcClient.sql("SELECT * FROM deployments WHERE project_id = :projectId AND external_deploy_id = :externalDeployId")
            .param("projectId", projectId)
            .param("externalDeployId", externalDeployId)
            .query(rowMapper)
            .optional();
    }

    public Optional<DeploymentRecord> findLatest(UUID projectId, String environment) {
        String sql = environment != null && !environment.isBlank()
            ? "SELECT * FROM deployments WHERE project_id = :projectId AND environment = :env ORDER BY created_at DESC LIMIT 1"
            : "SELECT * FROM deployments WHERE project_id = :projectId ORDER BY created_at DESC LIMIT 1";

        var query = jdbcClient.sql(sql).param("projectId", projectId);
        if (environment != null && !environment.isBlank()) {
            query = query.param("env", environment);
        }
        return query.query(rowMapper).optional();
    }

    public List<DeploymentRecord> findFailures(UUID projectId, int limit) {
        return jdbcClient.sql("""
            SELECT * FROM deployments
            WHERE project_id = :projectId AND (
                status ILIKE '%fail%' OR failure_classification IS NOT NULL AND failure_classification != 'NONE'
            )
            ORDER BY created_at DESC LIMIT :limit
            """)
            .param("projectId", projectId)
            .param("limit", limit > 0 ? limit : 10)
            .query(rowMapper)
            .list();
    }

    public List<DeploymentRecord> listByProject(UUID projectId, int limit) {
        return jdbcClient.sql("SELECT * FROM deployments WHERE project_id = :projectId ORDER BY created_at DESC LIMIT :limit")
            .param("projectId", projectId)
            .param("limit", limit > 0 ? limit : 50)
            .query(rowMapper)
            .list();
    }
}
