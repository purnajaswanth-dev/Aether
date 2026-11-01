package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.ActorType;
import com.context_platform.aether.workflow.model.AgentHandoffRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class AgentHandoffRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<AgentHandoffRecord> rowMapper;

    public AgentHandoffRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = (rs, rowNum) -> new AgentHandoffRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                ActorType.valueOf(rs.getString("from_actor_type")),
                rs.getString("from_actor_id"),
                ActorType.valueOf(rs.getString("to_actor_type")),
                rs.getString("to_actor_id"),
                rs.getObject("context_package_id", UUID.class),
                rs.getString("notes"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
        );
    }

    public AgentHandoffRecord save(AgentHandoffRecord handoff) {
        UUID id = handoff.id() != null ? handoff.id() : UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO agent_handoffs (id, project_id, task_id, from_actor_type, from_actor_id, to_actor_type, to_actor_id, context_package_id, notes, created_at)
                VALUES (:id, :projectId, :taskId, :fromActorType, :fromActorId, :toActorType, :toActorId, :contextPackageId, :notes, NOW())
                """)
                .param("id", id)
                .param("projectId", handoff.projectId())
                .param("taskId", handoff.taskId())
                .param("fromActorType", handoff.fromActorType().name())
                .param("fromActorId", handoff.fromActorId())
                .param("toActorType", handoff.toActorType().name())
                .param("toActorId", handoff.toActorId())
                .param("contextPackageId", handoff.contextPackageId())
                .param("notes", handoff.notes())
                .update();

        return new AgentHandoffRecord(
                id, handoff.projectId(), handoff.taskId(), handoff.fromActorType(),
                handoff.fromActorId(), handoff.toActorType(), handoff.toActorId(),
                handoff.contextPackageId(), handoff.notes(), Instant.now()
        );
    }

    public List<AgentHandoffRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM agent_handoffs
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at ASC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }
}

