package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.AgentFileMetadataRecord;
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
public class AgentFileMetadataRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<AgentFileMetadataRecord> rowMapper;

    public AgentFileMetadataRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            List<String> symbols = List.of();
            String json = rs.getString("symbols");
            if (json != null && !json.isBlank()) {
                try {
                    symbols = objectMapper.readValue(json, new TypeReference<List<String>>() {});
                } catch (JsonProcessingException ignored) {}
            }

            return new AgentFileMetadataRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("device_id", UUID.class),
                rs.getString("relative_path"),
                rs.getString("content_hash"),
                rs.getLong("file_size_bytes"),
                rs.getTimestamp("mtime").toInstant(),
                rs.getString("language"),
                symbols,
                rs.getTimestamp("updated_at").toInstant()
            );
        };
    }

    public void upsert(AgentFileMetadataRecord record) {
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();
        String symbolsJson;
        try {
            symbolsJson = objectMapper.writeValueAsString(record.symbols() != null ? record.symbols() : List.of());
        } catch (JsonProcessingException e) {
            symbolsJson = "[]";
        }

        jdbcClient.sql("""
            INSERT INTO agent_file_metadata (
                id, project_id, device_id, relative_path, content_hash, file_size_bytes, mtime, language, symbols, updated_at
            )
            VALUES (
                :id, :projectId, :deviceId, :relativePath, :contentHash, :fileSizeBytes, :mtime, :language, :symbols::jsonb, now()
            )
            ON CONFLICT (project_id, device_id, relative_path) DO UPDATE SET
                content_hash = EXCLUDED.content_hash,
                file_size_bytes = EXCLUDED.file_size_bytes,
                mtime = EXCLUDED.mtime,
                language = EXCLUDED.language,
                symbols = EXCLUDED.symbols,
                updated_at = now()
            """)
            .param("id", id)
            .param("projectId", record.projectId())
            .param("deviceId", record.deviceId())
            .param("relativePath", record.relativePath())
            .param("contentHash", record.contentHash())
            .param("fileSizeBytes", record.fileSizeBytes())
            .param("mtime", Timestamp.from(record.mtime()))
            .param("language", record.language())
            .param("symbols", symbolsJson)
            .update();
    }

    public List<AgentFileMetadataRecord> findRelevant(UUID projectId, String query, int limit) {
        int max = limit > 0 ? limit : 20;
        String searchTerm = query != null ? "%" + query.trim() + "%" : "%";

        return jdbcClient.sql("""
            SELECT * FROM agent_file_metadata
            WHERE project_id = :projectId AND (
                relative_path ILIKE :search OR language ILIKE :search OR symbols::text ILIKE :search
            )
            ORDER BY updated_at DESC LIMIT :limit
            """)
            .param("projectId", projectId)
            .param("search", searchTerm)
            .param("limit", max)
            .query(rowMapper)
            .list();
    }

    public List<AgentFileMetadataRecord> listByDevice(UUID projectId, UUID deviceId) {
        return jdbcClient.sql("SELECT * FROM agent_file_metadata WHERE project_id = :projectId AND device_id = :deviceId")
            .param("projectId", projectId)
            .param("deviceId", deviceId)
            .query(rowMapper)
            .list();
    }

    public void deleteByDeviceAndPath(UUID projectId, UUID deviceId, String relativePath) {
        jdbcClient.sql("DELETE FROM agent_file_metadata WHERE project_id = :projectId AND device_id = :deviceId AND relative_path = :path")
            .param("projectId", projectId)
            .param("deviceId", deviceId)
            .param("path", relativePath)
            .update();
    }
}
