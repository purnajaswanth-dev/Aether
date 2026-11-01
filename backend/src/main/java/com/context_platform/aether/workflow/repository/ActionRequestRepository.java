package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.security.RlsContextHelper;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.ActionRequestStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ActionRequestRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RlsContextHelper rlsContextHelper;
    private final RowMapper<ActionRequestRecord> rowMapper;

    public ActionRequestRepository(JdbcClient jdbcClient, ObjectMapper objectMapper, RlsContextHelper rlsContextHelper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rlsContextHelper = rlsContextHelper;
        this.rowMapper = (rs, rowNum) -> {
            Map<String, Object> params = Map.of();
            Map<String, Object> result = null;
            try {
                String pJson = rs.getString("parameters");
                if (pJson != null && !pJson.isBlank()) {
                    params = objectMapper.readValue(pJson, new TypeReference<>() {});
                }
                String rJson = rs.getString("execution_result");
                if (rJson != null && !rJson.isBlank()) {
                    result = objectMapper.readValue(rJson, new TypeReference<>() {});
                }
            } catch (Exception ignored) {}

            return new ActionRequestRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("project_id", UUID.class),
                    rs.getObject("agent_token_id", UUID.class),
                    rs.getString("action_type"),
                    params,
                    rs.getBoolean("is_destructive"),
                    ActionRequestStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("requested_at") != null ? rs.getTimestamp("requested_at").toInstant() : Instant.now(),
                    rs.getObject("reviewed_by", UUID.class),
                    rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null,
                    rs.getString("review_notes"),
                    result,
                    rs.getInt("lock_version"),
                    rs.getString("idempotency_key"),
                    rs.getTimestamp("claimed_at") != null ? rs.getTimestamp("claimed_at").toInstant() : null,
                    rs.getTimestamp("executed_at") != null ? rs.getTimestamp("executed_at").toInstant() : null
            );
        };
    }

    public ActionRequestRecord save(ActionRequestRecord record) {
        rlsContextHelper.setProjectContext(record.projectId());
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();
        String paramsJson = "{}";
        try {
            paramsJson = objectMapper.writeValueAsString(record.parameters() != null ? record.parameters() : Map.of());
        } catch (Exception ignored) {}

        jdbcClient.sql("""
                INSERT INTO action_requests (id, project_id, agent_token_id, action_type, parameters, is_destructive, status, requested_at, lock_version, idempotency_key)
                VALUES (:id, :projectId, :agentTokenId, :actionType, :parameters::jsonb, :isDestructive, :status, NOW(), :lockVersion, :idempotencyKey)
                """)
                .param("id", id)
                .param("projectId", record.projectId())
                .param("agentTokenId", record.agentTokenId())
                .param("actionType", record.actionType())
                .param("parameters", paramsJson)
                .param("isDestructive", record.isDestructive())
                .param("status", record.status().name())
                .param("lockVersion", record.lockVersion() > 0 ? record.lockVersion() : 1)
                .param("idempotencyKey", record.idempotencyKey())
                .update();

        return findById(record.projectId(), id).orElseThrow();
    }

    public int claimExecution(UUID projectId, UUID id, UUID reviewerId, String reviewNotes) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
                UPDATE action_requests
                SET status = 'EXECUTING',
                    reviewed_by = :reviewedBy,
                    reviewed_at = NOW(),
                    review_notes = :reviewNotes,
                    claimed_at = NOW(),
                    lock_version = lock_version + 1
                WHERE id = :id AND project_id = :projectId AND status = 'PENDING'
                """)
                .param("projectId", projectId)
                .param("id", id)
                .param("reviewedBy", reviewerId)
                .param("reviewNotes", reviewNotes)
                .update();
    }

    public int completeExecution(UUID projectId, UUID id, ActionRequestStatus finalStatus, Map<String, Object> result) {
        rlsContextHelper.setProjectContext(projectId);
        String resultJson = null;
        if (result != null) {
            try {
                resultJson = objectMapper.writeValueAsString(result);
            } catch (Exception ignored) {}
        }

        return jdbcClient.sql("""
                UPDATE action_requests
                SET status = :status,
                    executed_at = NOW(),
                    execution_result = :executionResult::jsonb,
                    lock_version = lock_version + 1
                WHERE id = :id AND project_id = :projectId AND status = 'EXECUTING'
                """)
                .param("projectId", projectId)
                .param("id", id)
                .param("status", finalStatus.name())
                .param("executionResult", resultJson)
                .update();
    }

    public int reject(UUID projectId, UUID id, UUID reviewerId, String reviewNotes) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
                UPDATE action_requests
                SET status = 'REJECTED',
                    reviewed_by = :reviewedBy,
                    reviewed_at = NOW(),
                    review_notes = :reviewNotes,
                    lock_version = lock_version + 1
                WHERE id = :id AND project_id = :projectId AND status = 'PENDING'
                """)
                .param("projectId", projectId)
                .param("id", id)
                .param("reviewedBy", reviewerId)
                .param("reviewNotes", reviewNotes)
                .update();
    }

    public void updateReview(UUID projectId, UUID id, ActionRequestStatus status, UUID reviewedBy, String reviewNotes, Map<String, Object> result) {
        rlsContextHelper.setProjectContext(projectId);
        String resultJson = null;
        if (result != null) {
            try {
                resultJson = objectMapper.writeValueAsString(result);
            } catch (Exception ignored) {}
        }

        jdbcClient.sql("""
                UPDATE action_requests
                SET status = :status,
                    reviewed_by = :reviewedBy,
                    reviewed_at = NOW(),
                    review_notes = :reviewNotes,
                    execution_result = :executionResult::jsonb,
                    lock_version = lock_version + 1
                WHERE project_id = :projectId AND id = :id
                """)
                .param("projectId", projectId)
                .param("id", id)
                .param("status", status.name())
                .param("reviewedBy", reviewedBy)
                .param("reviewNotes", reviewNotes)
                .param("executionResult", resultJson)
                .update();
    }

    public Optional<ActionRequestRecord> findById(UUID projectId, UUID id) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
                SELECT * FROM action_requests WHERE project_id = :projectId AND id = :id
                """)
                .param("projectId", projectId)
                .param("id", id)
                .query(rowMapper)
                .optional();
    }

    public Optional<ActionRequestRecord> findByIdempotencyKey(UUID projectId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
                SELECT * FROM action_requests WHERE project_id = :projectId AND idempotency_key = :idempotencyKey
                """)
                .param("projectId", projectId)
                .param("idempotencyKey", idempotencyKey)
                .query(rowMapper)
                .optional();
    }

    public List<ActionRequestRecord> findByProject(UUID projectId, ActionRequestStatus status) {
        rlsContextHelper.setProjectContext(projectId);
        if (status != null) {
            return jdbcClient.sql("""
                    SELECT * FROM action_requests
                    WHERE project_id = :projectId AND status = :status
                    ORDER BY requested_at DESC
                    """)
                    .param("projectId", projectId)
                    .param("status", status.name())
                    .query(rowMapper)
                    .list();
        }
        return jdbcClient.sql("""
                SELECT * FROM action_requests
                WHERE project_id = :projectId
                ORDER BY requested_at DESC
                """)
                .param("projectId", projectId)
                .query(rowMapper)
                .list();
    }
}
