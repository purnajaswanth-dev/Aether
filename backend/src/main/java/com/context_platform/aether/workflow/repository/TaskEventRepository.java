package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.ActorType;
import com.context_platform.aether.workflow.model.TaskEventRecord;
import com.context_platform.aether.workflow.model.TaskEventType;
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
public class TaskEventRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<TaskEventRecord> rowMapper;

    public TaskEventRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            Map<String, Object> metadata = Map.of();
            String metaJson = rs.getString("metadata");
            if (metaJson != null && !metaJson.isBlank()) {
                try {
                    metadata = objectMapper.readValue(metaJson, new TypeReference<>() {});
                } catch (Exception ignored) {}
            }
            return new TaskEventRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("project_id", UUID.class),
                    rs.getObject("task_id", UUID.class),
                    TaskEventType.valueOf(rs.getString("event_type")),
                    rs.getString("old_status"),
                    rs.getString("new_status"),
                    ActorType.valueOf(rs.getString("actor_type")),
                    rs.getString("actor_id"),
                    rs.getString("reason"),
                    metadata,
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
            );
        };
    }

    public TaskEventRecord save(TaskEventRecord event) {
        UUID id = event.id() != null ? event.id() : UUID.randomUUID();
        String metaJson = "{}";
        try {
            metaJson = objectMapper.writeValueAsString(event.metadata() != null ? event.metadata() : Map.of());
        } catch (Exception ignored) {}

        jdbcClient.sql("""
                INSERT INTO task_events (id, project_id, task_id, event_type, old_status, new_status, actor_type, actor_id, reason, metadata, created_at)
                VALUES (:id, :projectId, :taskId, :eventType, :oldStatus, :newStatus, :actorType, :actorId, :reason, :metadata::jsonb, NOW())
                """)
                .param("id", id)
                .param("projectId", event.projectId())
                .param("taskId", event.taskId())
                .param("eventType", event.eventType().name())
                .param("oldStatus", event.oldStatus())
                .param("newStatus", event.newStatus())
                .param("actorType", event.actorType().name())
                .param("actorId", event.actorId())
                .param("reason", event.reason())
                .param("metadata", metaJson)
                .update();

        return new TaskEventRecord(
                id, event.projectId(), event.taskId(), event.eventType(),
                event.oldStatus(), event.newStatus(), event.actorType(),
                event.actorId(), event.reason(), event.metadata(), Instant.now()
        );
    }

    public List<TaskEventRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM task_events
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at ASC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }
}

