package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.AgentInteractionRecord;
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
public class AgentInteractionRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<AgentInteractionRecord> rowMapper;

    public AgentInteractionRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            Map<String, Object> details = Map.of();
            String json = rs.getString("details");
            if (json != null && !json.isBlank()) {
                try {
                    details = objectMapper.readValue(json, new TypeReference<>() {});
                } catch (Exception ignored) {}
            }
            return new AgentInteractionRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("project_id", UUID.class),
                    rs.getObject("session_id", UUID.class),
                    rs.getObject("task_id", UUID.class),
                    rs.getString("interaction_type"),
                    rs.getString("tool_name"),
                    rs.getObject("artifact_id", UUID.class),
                    details,
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
            );
        };
    }

    public AgentInteractionRecord save(AgentInteractionRecord interaction) {
        UUID id = interaction.id() != null ? interaction.id() : UUID.randomUUID();
        String json = "{}";
        try {
            json = objectMapper.writeValueAsString(interaction.details() != null ? interaction.details() : Map.of());
        } catch (Exception ignored) {}

        jdbcClient.sql("""
                INSERT INTO agent_interactions (id, project_id, session_id, task_id, interaction_type, tool_name, artifact_id, details, created_at)
                VALUES (:id, :projectId, :sessionId, :taskId, :interactionType, :toolName, :artifactId, :details::jsonb, NOW())
                """)
                .param("id", id)
                .param("projectId", interaction.projectId())
                .param("sessionId", interaction.sessionId())
                .param("taskId", interaction.taskId())
                .param("interactionType", interaction.interactionType())
                .param("toolName", interaction.toolName())
                .param("artifactId", interaction.artifactId())
                .param("details", json)
                .update();

        return new AgentInteractionRecord(
                id, interaction.projectId(), interaction.sessionId(), interaction.taskId(),
                interaction.interactionType(), interaction.toolName(), interaction.artifactId(),
                interaction.details(), Instant.now()
        );
    }

    public List<AgentInteractionRecord> findBySessionId(UUID projectId, UUID sessionId) {
        return jdbcClient.sql("""
                SELECT * FROM agent_interactions
                WHERE project_id = :projectId AND session_id = :sessionId
                ORDER BY created_at ASC
                """)
                .param("projectId", projectId)
                .param("sessionId", sessionId)
                .query(rowMapper)
                .list();
    }

    public List<AgentInteractionRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM agent_interactions
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at ASC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }
}

