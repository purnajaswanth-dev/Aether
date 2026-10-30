package com.context_platform.aether.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RlsContextHelper {

    private static final Logger log = LoggerFactory.getLogger(RlsContextHelper.class);
    private final JdbcClient jdbcClient;

    public RlsContextHelper(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Set transaction-local project ID for PostgreSQL Row-Level Security.
     */
    public void setProjectContext(UUID projectId) {
        if (projectId != null) {
            jdbcClient.sql("SET LOCAL app.current_project_id = '" + projectId + "'").update();
            log.trace("RLS Context set to project: {}", projectId);
        } else {
            jdbcClient.sql("SET LOCAL app.current_project_id = ''").update();
        }
    }

    /**
     * Explicitly set RLS bypass flag for administrative transactions (e.g. project creation or user registration).
     */
    public void setBypassRls(boolean bypass) {
        jdbcClient.sql("SET LOCAL app.bypass_rls = '" + (bypass ? "on" : "off") + "'").update();
        log.trace("RLS Bypass set to: {}", bypass);
    }
}
