package com.context_platform.aether.event;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class AuditRepository {

    private final JdbcClient jdbcClient;

    public AuditRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void record(UUID projectId, UUID userId, String action, String actorType, String ipAddress, String userAgent, String detailsJson) {
        jdbcClient.sql("""
            INSERT INTO audit_events (id, project_id, user_id, action, actor_type, ip_address, user_agent, details, created_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?::jsonb, now())
        """)
        .param(projectId)
        .param(userId)
        .param(action)
        .param(actorType != null ? actorType : "HUMAN_USER")
        .param(ipAddress)
        .param(userAgent)
        .param(detailsJson != null ? detailsJson : "{}")
        .update();
    }

    public List<AuditEvent> listByUser(UUID userId, int limit) {
        return jdbcClient.sql("""
            SELECT id, project_id, user_id, action, actor_type, ip_address, user_agent, details, created_at
            FROM audit_events WHERE user_id = ?
            ORDER BY created_at DESC LIMIT ?
        """)
        .param(userId)
        .param(limit)
        .query(AuditEvent.class)
        .list();
    }
}

