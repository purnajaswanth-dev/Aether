package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.ProviderType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class IntegrationCheckpointRepository {

    private final JdbcClient jdbcClient;

    public IntegrationCheckpointRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<String> getCursor(UUID projectId, ProviderType provider, String resourceId) {
        return jdbcClient.sql("""
            SELECT cursor_value FROM integration_checkpoints
            WHERE project_id = :projectId AND provider = :provider AND resource_id = :resourceId
            """)
            .param("projectId", projectId)
            .param("provider", provider.name())
            .param("resourceId", resourceId)
            .query(String.class)
            .optional();
    }

    public void saveCheckpoint(UUID projectId, ProviderType provider, String resourceId, String cursorValue) {
        jdbcClient.sql("""
            INSERT INTO integration_checkpoints (id, project_id, provider, resource_id, cursor_value, checkpoint_timestamp)
            VALUES (:id, :projectId, :provider, :resourceId, :cursorValue, now())
            ON CONFLICT (project_id, provider, resource_id) DO UPDATE SET
                cursor_value = EXCLUDED.cursor_value,
                checkpoint_timestamp = now()
            """)
            .param("id", UUID.randomUUID())
            .param("projectId", projectId)
            .param("provider", provider.name())
            .param("resourceId", resourceId)
            .param("cursorValue", cursorValue)
            .update();
    }
}

