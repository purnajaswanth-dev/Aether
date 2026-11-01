package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.TaskPriority;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.model.TaskStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TaskRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<TaskRecord> rowMapper;

    public TaskRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = (rs, rowNum) -> new TaskRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getInt("task_number"),
                rs.getString("title"),
                rs.getString("description"),
                TaskStatus.valueOf(rs.getString("status")),
                TaskPriority.valueOf(rs.getString("priority")),
                rs.getObject("assignee_id", UUID.class),
                rs.getBoolean("proposed_by_agent"),
                rs.getString("agent_proposal_reason"),
                rs.getString("human_override_reason"),
                rs.getInt("lock_version"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : Instant.now()
        );
    }

    public TaskRecord create(TaskRecord task) {
        UUID id = task.id() != null ? task.id() : UUID.randomUUID();

        // Calculate next task_number per project
        Integer nextNumber = jdbcClient.sql("""
                SELECT COALESCE(MAX(task_number), 0) + 1 FROM tasks WHERE project_id = :projectId
                """)
                .param("projectId", task.projectId())
                .query(Integer.class)
                .single();

        jdbcClient.sql("""
                INSERT INTO tasks (id, project_id, task_number, title, description, status, priority, assignee_id, proposed_by_agent, agent_proposal_reason, human_override_reason, lock_version, created_by, created_at, updated_at)
                VALUES (:id, :projectId, :taskNumber, :title, :description, :status, :priority, :assigneeId, :proposedByAgent, :agentProposalReason, :humanOverrideReason, 1, :createdBy, NOW(), NOW())
                """)
                .param("id", id)
                .param("projectId", task.projectId())
                .param("taskNumber", nextNumber)
                .param("title", task.title())
                .param("description", task.description())
                .param("status", task.status().name())
                .param("priority", task.priority().name())
                .param("assigneeId", task.assigneeId())
                .param("proposedByAgent", task.proposedByAgent())
                .param("agentProposalReason", task.agentProposalReason())
                .param("humanOverrideReason", task.humanOverrideReason())
                .param("createdBy", task.createdBy())
                .update();

        return findById(task.projectId(), id).orElseThrow();
    }

    public TaskRecord update(TaskRecord task, int expectedLockVersion) {
        int updated = jdbcClient.sql("""
                UPDATE tasks
                SET title = :title,
                    description = :description,
                    status = :status,
                    priority = :priority,
                    assignee_id = :assigneeId,
                    proposed_by_agent = :proposedByAgent,
                    agent_proposal_reason = :agentProposalReason,
                    human_override_reason = :humanOverrideReason,
                    lock_version = lock_version + 1,
                    updated_at = NOW()
                WHERE id = :id AND project_id = :projectId AND lock_version = :expectedLockVersion
                """)
                .param("id", task.id())
                .param("projectId", task.projectId())
                .param("title", task.title())
                .param("description", task.description())
                .param("status", task.status().name())
                .param("priority", task.priority().name())
                .param("assigneeId", task.assigneeId())
                .param("proposedByAgent", task.proposedByAgent())
                .param("agentProposalReason", task.agentProposalReason())
                .param("humanOverrideReason", task.humanOverrideReason())
                .param("expectedLockVersion", expectedLockVersion)
                .update();

        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Task " + task.id() + " was modified by another transaction. Expected lock version: " + expectedLockVersion);
        }

        return findById(task.projectId(), task.id()).orElseThrow();
    }

    public Optional<TaskRecord> findById(UUID projectId, UUID id) {
        return jdbcClient.sql("""
                SELECT * FROM tasks WHERE project_id = :projectId AND id = :id
                """)
                .param("projectId", projectId)
                .param("id", id)
                .query(rowMapper)
                .optional();
    }

    public List<TaskRecord> findByProject(UUID projectId, TaskStatus status, int limit, int offset) {
        if (status != null) {
            return jdbcClient.sql("""
                    SELECT * FROM tasks
                    WHERE project_id = :projectId AND status = :status
                    ORDER BY task_number DESC
                    LIMIT :limit OFFSET :offset
                    """)
                    .param("projectId", projectId)
                    .param("status", status.name())
                    .param("limit", limit)
                    .param("offset", offset)
                    .query(rowMapper)
                    .list();
        }

        return jdbcClient.sql("""
                SELECT * FROM tasks
                WHERE project_id = :projectId
                ORDER BY task_number DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("projectId", projectId)
                .param("limit", limit)
                .param("offset", offset)
                .query(rowMapper)
                .list();
    }

    public int countByProject(UUID projectId, TaskStatus status) {
        if (status != null) {
            return jdbcClient.sql("""
                    SELECT COUNT(*) FROM tasks WHERE project_id = :projectId AND status = :status
                    """)
                    .param("projectId", projectId)
                    .param("status", status.name())
                    .query(Integer.class)
                    .single();
        }
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM tasks WHERE project_id = :projectId
                """)
                .param("projectId", projectId)
                .query(Integer.class)
                .single();
    }
}

