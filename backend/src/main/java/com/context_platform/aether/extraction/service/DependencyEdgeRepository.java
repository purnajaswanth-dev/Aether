package com.context_platform.aether.extraction.service;

import com.context_platform.aether.extraction.model.DependencyEdge;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.*;

@Repository
public class DependencyEdgeRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public DependencyEdgeRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public void save(DependencyEdge edge) {
        String meta = serializeMetadata(edge.metadata());
        jdbcClient.sql("""
            INSERT INTO dependency_edges (id, project_id, source_type, source_id, source_name, target_type, target_id, target_name, relation_type, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (id) DO NOTHING
            """)
            .params(
                edge.id(),
                edge.projectId(),
                edge.sourceType(),
                edge.sourceId(),
                edge.sourceName(),
                edge.targetType(),
                edge.targetId(),
                edge.targetName(),
                edge.relationType(),
                meta,
                Timestamp.from(edge.createdAt())
            )
            .update();
    }

    public void saveAll(List<DependencyEdge> edges) {
        for (DependencyEdge edge : edges) {
            save(edge);
        }
    }

    public List<DependencyEdge> listByProject(UUID projectId) {
        return jdbcClient.sql("""
            SELECT id, project_id, source_type, source_id, source_name, target_type, target_id, target_name, relation_type, metadata::text, created_at
            FROM dependency_edges
            WHERE project_id = ?
            ORDER BY created_at DESC
            """)
            .param(projectId)
            .query((rs, rowNum) -> new DependencyEdge(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("source_type"),
                rs.getObject("source_id", UUID.class),
                rs.getString("source_name"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getString("target_name"),
                rs.getString("relation_type"),
                deserializeMetadata(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public List<DependencyEdge> findConnectedGraph(UUID projectId, UUID rootId, int maxDepth) {
        return jdbcClient.sql("""
            WITH RECURSIVE graph_cte AS (
                -- Base query: direct outbound or inbound edges from root
                SELECT id, project_id, source_type, source_id, source_name,
                       target_type, target_id, target_name, relation_type, metadata, created_at, 1 as depth
                FROM dependency_edges
                WHERE project_id = ? AND (source_id = ? OR target_id = ?)
                
                UNION
                
                -- Recursive step: traverse up to maxDepth
                SELECT e.id, e.project_id, e.source_type, e.source_id, e.source_name,
                       e.target_type, e.target_id, e.target_name, e.relation_type, e.metadata, e.created_at, g.depth + 1
                FROM dependency_edges e
                INNER JOIN graph_cte g ON (e.source_id = g.target_id OR e.target_id = g.source_id)
                WHERE e.project_id = ? AND g.depth < ?
            )
            SELECT DISTINCT id, project_id, source_type, source_id, source_name,
                            target_type, target_id, target_name, relation_type, metadata::text, created_at
            FROM graph_cte
            """)
            .params(projectId, rootId, rootId, projectId, maxDepth)
            .query((rs, rowNum) -> new DependencyEdge(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("source_type"),
                rs.getObject("source_id", UUID.class),
                rs.getString("source_name"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getString("target_name"),
                rs.getString("relation_type"),
                deserializeMetadata(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public void deleteByArtifact(UUID projectId, UUID artifactId) {
        jdbcClient.sql("""
            DELETE FROM dependency_edges
            WHERE project_id = ? AND (source_id = ? OR target_id = ?)
            """)
            .params(projectId, artifactId, artifactId)
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

