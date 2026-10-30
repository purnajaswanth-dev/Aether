package com.context_platform.aether.event;

import com.context_platform.aether.security.RlsContextHelper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class TimelineRepository {

    private final JdbcClient jdbcClient;
    private final RlsContextHelper rlsContextHelper;

    public TimelineRepository(JdbcClient jdbcClient, RlsContextHelper rlsContextHelper) {
        this.jdbcClient = jdbcClient;
        this.rlsContextHelper = rlsContextHelper;
    }

    public TimelineEvent append(
        UUID projectId,
        UUID taskId,
        String sourcePlatform,
        String eventType,
        UUID associatedArtifactId,
        UUID associatedDecisionId,
        Integer decisionVersion,
        String status,
        String actorId,
        String actorType,
        String summary,
        String metadataJson
    ) {
        rlsContextHelper.setProjectContext(projectId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcClient.sql("""
            INSERT INTO timeline_events (
                id, project_id, task_id, source_platform, timestamp, event_type,
                associated_artifact_id, associated_decision_id, decision_version,
                status, actor_id, actor_type, summary, metadata, created_at
            ) VALUES (?, ?, ?, ?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
        """)
        .param(id)
        .param(projectId)
        .param(taskId)
        .param(sourcePlatform != null ? sourcePlatform : "PLATFORM")
        .param(eventType)
        .param(associatedArtifactId)
        .param(associatedDecisionId)
        .param(decisionVersion)
        .param(status != null ? status : "SUCCESS")
        .param(actorId != null ? actorId : "SYSTEM")
        .param(actorType != null ? actorType : "HUMAN_USER")
        .param(summary)
        .param(metadataJson != null ? metadataJson : "{}")
        .update();

        return new TimelineEvent(
            id, projectId, taskId, sourcePlatform, now, eventType,
            associatedArtifactId, associatedDecisionId, decisionVersion,
            status, actorId, actorType, summary, metadataJson, now
        );
    }

    public List<TimelineEvent> listByProject(UUID projectId, String eventType, String sourcePlatform, int limit) {
        rlsContextHelper.setProjectContext(projectId);
        var sql = new StringBuilder("""
            SELECT id, project_id, task_id, source_platform, timestamp, event_type,
                   associated_artifact_id, associated_decision_id, decision_version,
                   status, actor_id, actor_type, summary, metadata, created_at
            FROM timeline_events
            WHERE project_id = ?
        """);

        if (eventType != null && !eventType.isBlank()) {
            sql.append(" AND event_type = '").append(eventType).append("'");
        }
        if (sourcePlatform != null && !sourcePlatform.isBlank()) {
            sql.append(" AND source_platform = '").append(sourcePlatform).append("'");
        }
        sql.append(" ORDER BY timestamp DESC LIMIT ?");

        return jdbcClient.sql(sql.toString())
            .param(projectId)
            .param(limit > 0 ? limit : 100)
            .query(TimelineEvent.class)
            .list();
    }
}
