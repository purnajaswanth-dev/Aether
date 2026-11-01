package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.IntegrationConfigRecord;
import com.context_platform.aether.integration.model.IntegrationStatus;
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
public class IntegrationConfigRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<IntegrationConfigRecord> rowMapper;

    public IntegrationConfigRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            Timestamp lastSyncTs = rs.getTimestamp("last_sync_at");
            Timestamp createdTs = rs.getTimestamp("created_at");
            Timestamp updatedTs = rs.getTimestamp("updated_at");

            Map<String, Object> configMetadata = Map.of();
            String json = rs.getString("config_metadata");
            if (json != null && !json.isBlank()) {
                try {
                    configMetadata = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                } catch (JsonProcessingException ignored) {}
            }

            return new IntegrationConfigRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                ProviderType.valueOf(rs.getString("provider")),
                IntegrationStatus.valueOf(rs.getString("status")),
                configMetadata,
                lastSyncTs != null ? lastSyncTs.toInstant() : null,
                createdTs != null ? createdTs.toInstant() : Instant.now(),
                updatedTs != null ? updatedTs.toInstant() : Instant.now()
            );
        };
    }

    public IntegrationConfigRecord save(IntegrationConfigRecord record) {
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(record.configMetadata() != null ? record.configMetadata() : Map.of());
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }

        jdbcClient.sql("""
            INSERT INTO integration_configs (id, project_id, provider, status, config_metadata, last_sync_at, created_at, updated_at)
            VALUES (:id, :projectId, :provider, :status, :metadata::jsonb, :lastSyncAt, now(), now())
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                config_metadata = EXCLUDED.config_metadata,
                last_sync_at = EXCLUDED.last_sync_at,
                updated_at = now()
            """)
            .param("id", id)
            .param("projectId", record.projectId())
            .param("provider", record.provider().name())
            .param("status", record.status().name())
            .param("metadata", metadataJson)
            .param("lastSyncAt", record.lastSyncAt() != null ? Timestamp.from(record.lastSyncAt()) : null)
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<IntegrationConfigRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM integration_configs WHERE id = :id")
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public Optional<IntegrationConfigRecord> findByProjectIdAndProvider(UUID projectId, ProviderType provider) {
        return jdbcClient.sql("SELECT * FROM integration_configs WHERE project_id = :projectId AND provider = :provider ORDER BY created_at DESC LIMIT 1")
            .param("projectId", projectId)
            .param("provider", provider.name())
            .query(rowMapper)
            .optional();
    }

    public List<IntegrationConfigRecord> findByProjectId(UUID projectId) {
        return jdbcClient.sql("SELECT * FROM integration_configs WHERE project_id = :projectId ORDER BY provider ASC")
            .param("projectId", projectId)
            .query(rowMapper)
            .list();
    }

    public void updateStatus(UUID id, IntegrationStatus status) {
        jdbcClient.sql("UPDATE integration_configs SET status = :status, updated_at = now() WHERE id = :id")
            .param("id", id)
            .param("status", status.name())
            .update();
    }

    public void updateLastSyncAt(UUID id, Instant lastSyncAt) {
        jdbcClient.sql("UPDATE integration_configs SET last_sync_at = :lastSyncAt, updated_at = now() WHERE id = :id")
            .param("id", id)
            .param("lastSyncAt", Timestamp.from(lastSyncAt))
            .update();
    }

    public void delete(UUID id) {
        jdbcClient.sql("DELETE FROM integration_configs WHERE id = :id")
            .param("id", id)
            .update();
    }
}
