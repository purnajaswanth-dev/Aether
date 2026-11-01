package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.DeadLetterEventRecord;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeadLetterEventRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<DeadLetterEventRecord> rowMapper;

    public DeadLetterEventRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            Map<String, Object> headers = Map.of();
            String json = rs.getString("headers");
            if (json != null && !json.isBlank()) {
                try {
                    headers = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException ignored) {}
            }

            Timestamp lastRetryTs = rs.getTimestamp("last_retry_at");

            return new DeadLetterEventRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                ProviderType.valueOf(rs.getString("provider")),
                rs.getString("event_type"),
                rs.getString("payload"),
                headers,
                rs.getString("error_message"),
                rs.getInt("attempts"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                lastRetryTs != null ? lastRetryTs.toInstant() : null
            );
        };
    }

    public DeadLetterEventRecord recordFailedEvent(
        UUID projectId,
        ProviderType provider,
        String eventType,
        String payload,
        Map<String, ?> headers,
        String errorMessage
    ) {
        UUID id = UUID.randomUUID();
        String headersJson;
        try {
            headersJson = objectMapper.writeValueAsString(headers != null ? headers : Map.of());
        } catch (JsonProcessingException e) {
            headersJson = "{}";
        }

        jdbcClient.sql("""
            INSERT INTO dead_letter_events (
                id, project_id, provider, event_type, payload, headers, error_message, attempts, status, created_at
            )
            VALUES (
                :id, :projectId, :provider, :eventType, :payload, :headers::jsonb, :errorMessage, 1, 'PENDING', now()
            )
            """)
            .param("id", id)
            .param("projectId", projectId)
            .param("provider", provider.name())
            .param("eventType", eventType)
            .param("payload", payload != null ? payload : "")
            .param("headers", headersJson)
            .param("errorMessage", errorMessage != null ? errorMessage : "Unknown error")
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<DeadLetterEventRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM dead_letter_events WHERE id = :id")
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public List<DeadLetterEventRecord> listPending(UUID projectId) {
        return jdbcClient.sql("SELECT * FROM dead_letter_events WHERE project_id = :projectId AND status = 'PENDING' ORDER BY created_at DESC")
            .param("projectId", projectId)
            .query(rowMapper)
            .list();
    }

    public void markRetried(UUID id) {
        jdbcClient.sql("UPDATE dead_letter_events SET status = 'RETRIED', attempts = attempts + 1, last_retry_at = now() WHERE id = :id")
            .param("id", id)
            .update();
    }

    public void incrementAttempts(UUID id, String lastError) {
        jdbcClient.sql("UPDATE dead_letter_events SET attempts = attempts + 1, error_message = :lastError, last_retry_at = now() WHERE id = :id")
            .param("id", id)
            .param("lastError", lastError)
            .update();
    }

    public void markDiscarded(UUID id) {
        jdbcClient.sql("UPDATE dead_letter_events SET status = 'DISCARDED' WHERE id = :id")
            .param("id", id)
            .update();
    }
}
