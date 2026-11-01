package com.context_platform.aether.orchestrator.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class ContextRetrievalLogRepository {

    private final JdbcClient jdbcClient;

    public ContextRetrievalLogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void log(UUID packageId, UUID projectId, String retrievalMethod, String queryText, int resultsCount, long durationMs) {
        jdbcClient.sql("""
            INSERT INTO context_retrieval_logs (id, package_id, project_id, retrieval_method, query_text, results_count, duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """)
            .params(
                UUID.randomUUID(),
                packageId,
                projectId,
                retrievalMethod,
                queryText,
                resultsCount,
                durationMs,
                Timestamp.from(Instant.now())
            )
            .update();
    }
}

