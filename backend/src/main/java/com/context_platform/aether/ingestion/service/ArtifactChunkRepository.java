package com.context_platform.aether.ingestion.service;

import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.ChunkType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.*;

@Repository
public class ArtifactChunkRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public ArtifactChunkRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public void save(ArtifactChunk chunk) {
        String metadataJson = serializeMetadata(chunk.metadata());
        jdbcClient.sql("""
            INSERT INTO artifact_chunks (
                id, project_id, artifact_id, artifact_version, chunk_index,
                parent_section_path, chunk_type, content, content_hash,
                start_offset, end_offset, start_line, end_line, metadata, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (id) DO NOTHING
            """)
            .params(
                chunk.id(),
                chunk.projectId(),
                chunk.artifactId(),
                chunk.artifactVersion(),
                chunk.chunkIndex(),
                chunk.parentSectionPath(),
                chunk.chunkType().name(),
                chunk.content(),
                chunk.contentHash(),
                chunk.startOffset(),
                chunk.endOffset(),
                chunk.startLine(),
                chunk.endLine(),
                metadataJson,
                Timestamp.from(chunk.createdAt())
            )
            .update();
    }

    public void saveAll(List<ArtifactChunk> chunks) {
        for (ArtifactChunk chunk : chunks) {
            save(chunk);
        }
    }

    public Optional<ArtifactChunk> findById(UUID chunkId) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, chunk_index,
                   parent_section_path, chunk_type, content, content_hash,
                   start_offset, end_offset, start_line, end_line, metadata::text, created_at
            FROM artifact_chunks
            WHERE id = ?
            """)
            .param(chunkId)
            .query((rs, rowNum) -> new ArtifactChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                rs.getInt("chunk_index"),
                rs.getString("parent_section_path"),
                ChunkType.valueOf(rs.getString("chunk_type")),
                rs.getString("content"),
                rs.getString("content_hash"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getInt("start_line"),
                rs.getInt("end_line"),
                deserializeMetadata(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional();
    }

    public List<ArtifactChunk> listByArtifact(UUID projectId, UUID artifactId, int version) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, chunk_index,
                   parent_section_path, chunk_type, content, content_hash,
                   start_offset, end_offset, start_line, end_line, metadata::text, created_at
            FROM artifact_chunks
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            ORDER BY chunk_index ASC
            """)
            .params(projectId, artifactId, version)
            .query((rs, rowNum) -> new ArtifactChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                rs.getInt("chunk_index"),
                rs.getString("parent_section_path"),
                ChunkType.valueOf(rs.getString("chunk_type")),
                rs.getString("content"),
                rs.getString("content_hash"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getInt("start_line"),
                rs.getInt("end_line"),
                deserializeMetadata(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public List<ArtifactChunk> listByProject(UUID projectId, int limit) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, chunk_index,
                   parent_section_path, chunk_type, content, content_hash,
                   start_offset, end_offset, start_line, end_line, metadata::text, created_at
            FROM artifact_chunks
            WHERE project_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """)
            .params(projectId, limit)
            .query((rs, rowNum) -> new ArtifactChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                rs.getInt("chunk_index"),
                rs.getString("parent_section_path"),
                ChunkType.valueOf(rs.getString("chunk_type")),
                rs.getString("content"),
                rs.getString("content_hash"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getInt("start_line"),
                rs.getInt("end_line"),
                deserializeMetadata(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public void deleteByArtifactVersion(UUID projectId, UUID artifactId, int version) {
        jdbcClient.sql("""
            DELETE FROM artifact_chunks
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            """)
            .params(projectId, artifactId, version)
            .update();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}

