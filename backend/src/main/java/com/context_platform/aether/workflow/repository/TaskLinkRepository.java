package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.TaskLinkRecord;
import com.context_platform.aether.workflow.model.TaskLinkType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class TaskLinkRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<TaskLinkRecord> rowMapper;

    public TaskLinkRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = (rs, rowNum) -> new TaskLinkRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                TaskLinkType.valueOf(rs.getString("link_type")),
                rs.getString("target_id"),
                rs.getString("target_label"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
        );
    }

    public TaskLinkRecord save(TaskLinkRecord link) {
        UUID id = link.id() != null ? link.id() : UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO task_links (id, project_id, task_id, link_type, target_id, target_label, created_at)
                VALUES (:id, :projectId, :taskId, :linkType, :targetId, :targetLabel, NOW())
                ON CONFLICT (project_id, task_id, link_type, target_id) DO UPDATE
                SET target_label = EXCLUDED.target_label
                """)
                .param("id", id)
                .param("projectId", link.projectId())
                .param("taskId", link.taskId())
                .param("linkType", link.linkType().name())
                .param("targetId", link.targetId())
                .param("targetLabel", link.targetLabel())
                .update();

        return new TaskLinkRecord(
                id, link.projectId(), link.taskId(), link.linkType(),
                link.targetId(), link.targetLabel(), Instant.now()
        );
    }

    public List<TaskLinkRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM task_links
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at ASC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }

    public void deleteLink(UUID projectId, UUID taskId, TaskLinkType linkType, String targetId) {
        jdbcClient.sql("""
                DELETE FROM task_links
                WHERE project_id = :projectId AND task_id = :taskId AND link_type = :linkType AND target_id = :targetId
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("linkType", linkType.name())
                .param("targetId", targetId)
                .update();
    }

    public boolean hasLinkType(UUID projectId, UUID taskId, TaskLinkType linkType) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM task_links
                WHERE project_id = :projectId AND task_id = :taskId AND link_type = :linkType
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("linkType", linkType.name())
                .query(Integer.class)
                .single();

        return count != null && count > 0;
    }
}

