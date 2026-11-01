package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.CodeDependencyRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class CodeDependencyRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<CodeDependencyRecord> rowMapper;

    public CodeDependencyRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            Map<String, Object> meta = Map.of();
            try {
                String mJson = rs.getString("metadata");
                if (mJson != null && !mJson.isBlank()) {
                    meta = objectMapper.readValue(mJson, new TypeReference<>() {});
                }
            } catch (Exception ignored) {}

            return new CodeDependencyRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("project_id", UUID.class),
                    rs.getString("source_path"),
                    rs.getString("target_path"),
                    rs.getString("dependency_type"),
                    rs.getString("confidence"),
                    meta,
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
            );
        };
    }

    public void save(CodeDependencyRecord record) {
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();
        String mJson = "{}";
        try {
            mJson = objectMapper.writeValueAsString(record.metadata() != null ? record.metadata() : Map.of());
        } catch (Exception ignored) {}

        jdbcClient.sql("""
                INSERT INTO code_dependencies (id, project_id, source_path, target_path, dependency_type, confidence, metadata, created_at)
                VALUES (:id, :projectId, :sourcePath, :targetPath, :dependencyType, :confidence, :metadata::jsonb, NOW())
                """)
                .param("id", id)
                .param("projectId", record.projectId())
                .param("sourcePath", record.sourcePath())
                .param("targetPath", record.targetPath())
                .param("dependencyType", record.dependencyType())
                .param("confidence", record.confidence())
                .param("metadata", mJson)
                .update();
    }

    public List<CodeDependencyRecord> findBySourcePath(UUID projectId, String sourcePath) {
        return jdbcClient.sql("""
                SELECT * FROM code_dependencies
                WHERE project_id = :projectId AND source_path = :sourcePath
                """)
                .param("projectId", projectId)
                .param("sourcePath", sourcePath)
                .query(rowMapper)
                .list();
    }

    public List<CodeDependencyRecord> findByTargetPath(UUID projectId, String targetPath) {
        return jdbcClient.sql("""
                SELECT * FROM code_dependencies
                WHERE project_id = :projectId AND target_path = :targetPath
                """)
                .param("projectId", projectId)
                .param("targetPath", targetPath)
                .query(rowMapper)
                .list();
    }

    public List<CodeDependencyRecord> findAllForProject(UUID projectId) {
        return jdbcClient.sql("""
                SELECT * FROM code_dependencies WHERE project_id = :projectId
                """)
                .param("projectId", projectId)
                .query(rowMapper)
                .list();
    }

    public void deleteBySourcePath(UUID projectId, String sourcePath) {
        jdbcClient.sql("""
                DELETE FROM code_dependencies WHERE project_id = :projectId AND source_path = :sourcePath
                """)
                .param("projectId", projectId)
                .param("sourcePath", sourcePath)
                .update();
    }
}

