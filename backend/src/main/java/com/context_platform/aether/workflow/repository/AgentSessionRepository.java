package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.AgentSessionRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AgentSessionRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<AgentSessionRecord> rowMapper;

    public AgentSessionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = (rs, rowNum) -> new AgentSessionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("client_platform"),
                rs.getObject("task_id", UUID.class),
                rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : Instant.now(),
                rs.getTimestamp("ended_at") != null ? rs.getTimestamp("ended_at").toInstant() : null,
                rs.getString("status"),
                rs.getString("summary"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
        );
    }

    public AgentSessionRecord save(AgentSessionRecord session) {
        UUID id = session.id() != null ? session.id() : UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO agent_sessions (id, project_id, client_platform, task_id, started_at, ended_at, status, summary, created_at)
                VALUES (:id, :projectId, :clientPlatform, :taskId, :startedAt, :endedAt, :status, :summary, NOW())
                ON CONFLICT (id) DO UPDATE
                SET status = EXCLUDED.status,
                    summary = EXCLUDED.summary,
                    ended_at = EXCLUDED.ended_at
                """)
                .param("id", id)
                .param("projectId", session.projectId())
                .param("clientPlatform", session.clientPlatform())
                .param("taskId", session.taskId())
                .param("startedAt", session.startedAt() != null ? Timestamp.from(session.startedAt()) : Timestamp.from(Instant.now()))
                .param("endedAt", session.endedAt() != null ? Timestamp.from(session.endedAt()) : null)
                .param("status", session.status())
                .param("summary", session.summary())
                .update();

        return findById(session.projectId(), id).orElseThrow();
    }

    public Optional<AgentSessionRecord> findById(UUID projectId, UUID id) {
        return jdbcClient.sql("""
                SELECT * FROM agent_sessions WHERE project_id = :projectId AND id = :id
                """)
                .param("projectId", projectId)
                .param("id", id)
                .query(rowMapper)
                .optional();
    }

    public Optional<AgentSessionRecord> findActiveSession(UUID projectId, String clientPlatform) {
        return jdbcClient.sql("""
                SELECT * FROM agent_sessions
                WHERE project_id = :projectId AND client_platform = :clientPlatform AND status = 'ACTIVE'
                ORDER BY started_at DESC
                LIMIT 1
                """)
                .param("projectId", projectId)
                .param("clientPlatform", clientPlatform)
                .query(rowMapper)
                .optional();
    }

    public List<AgentSessionRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM agent_sessions
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY started_at DESC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }

    public void closeSession(UUID projectId, UUID sessionId, String summary) {
        jdbcClient.sql("""
                UPDATE agent_sessions
                SET status = 'COMPLETED',
                    summary = :summary,
                    ended_at = NOW()
                WHERE project_id = :projectId AND id = :sessionId
                """)
                .param("projectId", projectId)
                .param("sessionId", sessionId)
                .param("summary", summary)
                .update();
    }
}

