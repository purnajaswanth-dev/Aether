package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.ProviderType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProcessedEventRepository {

    private final JdbcClient jdbcClient;

    public ProcessedEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean isProcessed(UUID projectId, ProviderType provider, String eventId) {
        Integer count = jdbcClient.sql("""
            SELECT count(1) FROM processed_events
            WHERE project_id = :projectId AND provider = :provider AND event_id = :eventId
            """)
            .param("projectId", projectId)
            .param("provider", provider.name())
            .param("eventId", eventId)
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }

    public boolean isIdempotencyKeyUsed(UUID projectId, String idempotencyKey) {
        Integer count = jdbcClient.sql("""
            SELECT count(1) FROM processed_events
            WHERE project_id = :projectId AND idempotency_key = :idempotencyKey
            """)
            .param("projectId", projectId)
            .param("idempotencyKey", idempotencyKey)
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }

    public void recordEvent(
        UUID projectId,
        ProviderType provider,
        String eventId,
        String eventType,
        String idempotencyKey,
        UUID rawArtifactId
    ) {
        jdbcClient.sql("""
            INSERT INTO processed_events (
                id, project_id, provider, event_id, event_type, idempotency_key, raw_artifact_id, status, processed_at
            )
            VALUES (
                :id, :projectId, :provider, :eventId, :eventType, :idempotencyKey, :rawArtifactId, 'PROCESSED', now()
            )
            ON CONFLICT (project_id, provider, event_id) DO NOTHING
            """)
            .param("id", UUID.randomUUID())
            .param("projectId", projectId)
            .param("provider", provider.name())
            .param("eventId", eventId)
            .param("eventType", eventType)
            .param("idempotencyKey", idempotencyKey)
            .param("rawArtifactId", rawArtifactId)
            .update();
    }
}

