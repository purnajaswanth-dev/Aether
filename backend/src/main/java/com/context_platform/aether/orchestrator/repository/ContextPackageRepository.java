package com.context_platform.aether.orchestrator.repository;

import com.context_platform.aether.orchestrator.model.ContextPackageResponse;
import com.context_platform.aether.orchestrator.model.ContextRequestType;
import com.context_platform.aether.orchestrator.model.OmittedItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class ContextPackageRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public ContextPackageRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public void save(ContextPackageResponse pkg, String taskDescription) {
        String payloadJson = serialize(pkg.sections());
        String omittedJson = serialize(pkg.omittedItems());

        jdbcClient.sql("""
            INSERT INTO context_packages (
                id, project_id, request_type, task_description, target_model,
                token_budget, estimated_tokens, package_payload, omitted_items, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (id) DO NOTHING
            """)
            .params(
                pkg.packageId(),
                pkg.projectId(),
                pkg.requestType().name(),
                taskDescription,
                pkg.targetModel(),
                pkg.tokenBudget(),
                pkg.estimatedTokens(),
                payloadJson,
                omittedJson,
                Timestamp.from(pkg.createdAt())
            )
            .update();
    }

    public Optional<ContextPackageResponse> findById(UUID packageId) {
        return jdbcClient.sql("""
            SELECT id, project_id, request_type, task_description, target_model,
                   token_budget, estimated_tokens, package_payload::text, omitted_items::text, created_at
            FROM context_packages
            WHERE id = ?
            """)
            .param(packageId)
            .query((rs, rowNum) -> new ContextPackageResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                ContextRequestType.valueOf(rs.getString("request_type")),
                rs.getString("target_model"),
                rs.getInt("token_budget"),
                rs.getInt("estimated_tokens"),
                deserializeSections(rs.getString("package_payload")),
                deserializeOmitted(rs.getString("omitted_items")),
                "",
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional();
    }

    public List<ContextPackageResponse> listRecent(UUID projectId, int limit) {
        return jdbcClient.sql("""
            SELECT id, project_id, request_type, task_description, target_model,
                   token_budget, estimated_tokens, package_payload::text, omitted_items::text, created_at
            FROM context_packages
            WHERE project_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """)
            .params(projectId, limit)
            .query((rs, rowNum) -> new ContextPackageResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                ContextRequestType.valueOf(rs.getString("request_type")),
                rs.getString("target_model"),
                rs.getInt("token_budget"),
                rs.getInt("estimated_tokens"),
                deserializeSections(rs.getString("package_payload")),
                deserializeOmitted(rs.getString("omitted_items")),
                "",
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> deserializeSections(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private List<OmittedItem> deserializeOmitted(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<OmittedItem>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}

