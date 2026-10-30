package com.context_platform.aether.mcp.repository;

import com.context_platform.aether.mcp.model.McpCallLog;
import com.context_platform.aether.mcp.model.McpClientPlatform;
import com.context_platform.aether.mcp.model.McpReliabilityReportResponse;
import com.context_platform.aether.security.RlsContextHelper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class McpCallLogRepository {

    private final JdbcClient jdbcClient;
    private final RlsContextHelper rlsContextHelper;

    public McpCallLogRepository(JdbcClient jdbcClient, RlsContextHelper rlsContextHelper) {
        this.jdbcClient = jdbcClient;
        this.rlsContextHelper = rlsContextHelper;
    }

    private final RowMapper<McpCallLog> rowMapper = (rs, rowNum) -> {
        Timestamp createdTs = rs.getTimestamp("created_at");
        return new McpCallLog(
            rs.getObject("id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getObject("token_id", UUID.class),
            rs.getString("tool_name"),
            McpClientPlatform.valueOf(rs.getString("client_platform")),
            rs.getLong("duration_ms"),
            rs.getString("status"),
            rs.getString("error_message"),
            createdTs != null ? createdTs.toInstant() : Instant.now()
        );
    };

    public void save(McpCallLog log) {
        UUID id = log.id() != null ? log.id() : UUID.randomUUID();
        jdbcClient.sql("""
            INSERT INTO mcp_call_logs (
                id, project_id, token_id, tool_name, client_platform,
                duration_ms, status, error_message, created_at
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, ?, now()
            )
        """)
        .param(id)
        .param(log.projectId())
        .param(log.tokenId())
        .param(log.toolName())
        .param(log.clientPlatform().name())
        .param(log.durationMs())
        .param(log.status())
        .param(log.errorMessage())
        .update();
    }

    public List<McpCallLog> findByProjectId(UUID projectId, int limit, int offset, String statusFilter, String toolFilter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM mcp_call_logs WHERE project_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(projectId);

        if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase("ALL")) {
            sql.append(" AND status = ?");
            params.add(statusFilter.toUpperCase());
        }

        if (toolFilter != null && !toolFilter.isBlank() && !toolFilter.equalsIgnoreCase("ALL")) {
            sql.append(" AND tool_name = ?");
            params.add(toolFilter);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit > 0 ? limit : 50);
        params.add(offset >= 0 ? offset : 0);

        var query = jdbcClient.sql(sql.toString());
        for (Object p : params) {
            query.param(p);
        }
        return query.query(rowMapper).list();
    }

    public McpReliabilityReportResponse calculateReliabilityMetrics(UUID projectId) {
        Long total = jdbcClient.sql("SELECT COUNT(*) FROM mcp_call_logs WHERE project_id = ?")
            .param(projectId)
            .query(Long.class)
            .single();
        long totalCalls = total != null ? total : 0;

        Long success = jdbcClient.sql("SELECT COUNT(*) FROM mcp_call_logs WHERE project_id = ? AND status = 'SUCCESS'")
            .param(projectId)
            .query(Long.class)
            .single();
        long successfulCalls = success != null ? success : 0;
        long failedCalls = totalCalls - successfulCalls;

        // Platform distribution
        Map<String, Long> platformDist = new LinkedHashMap<>();
        jdbcClient.sql("SELECT client_platform, COUNT(*) as cnt FROM mcp_call_logs WHERE project_id = ? GROUP BY client_platform ORDER BY cnt DESC")
            .param(projectId)
            .query((rs, rowNum) -> Map.entry(rs.getString("client_platform"), rs.getLong("cnt")))
            .list()
            .forEach(entry -> platformDist.put(entry.getKey(), entry.getValue()));

        // Top tools called
        Map<String, Long> topTools = new LinkedHashMap<>();
        jdbcClient.sql("SELECT tool_name, COUNT(*) as cnt FROM mcp_call_logs WHERE project_id = ? GROUP BY tool_name ORDER BY cnt DESC LIMIT 10")
            .param(projectId)
            .query((rs, rowNum) -> Map.entry(rs.getString("tool_name"), rs.getLong("cnt")))
            .list()
            .forEach(entry -> topTools.put(entry.getKey(), entry.getValue()));

        // Distinct sessions (tokens)
        List<UUID> tokenIds = jdbcClient.sql("SELECT DISTINCT token_id FROM mcp_call_logs WHERE project_id = ? AND token_id IS NOT NULL")
            .param(projectId)
            .query(UUID.class)
            .list();
        int sessionsTracked = tokenIds.size();

        if (sessionsTracked == 0) {
            return new McpReliabilityReportResponse(
                totalCalls, successfulCalls, failedCalls, 0, 100.0, 100.0, topTools, platformDist
            );
        }

        // Measure for each token: did retrieval occur before first save, and did a save occur?
        int sessionsWithRetrieveBeforeSave = 0;
        int sessionsWithSave = 0;

        Set<String> retrieveTools = Set.of(
            "list_projects", "get_project_overview", "get_project_context",
            "get_project_status", "search_project_memory", "get_original_artifact",
            "get_architecture", "get_decisions", "get_recent_activity"
        );
        Set<String> saveTools = Set.of(
            "save_project_artifact", "save_conversation_output", "record_decision", "record_review_finding"
        );

        for (UUID tokenId : tokenIds) {
            List<String> orderedTools = jdbcClient.sql("""
                SELECT tool_name FROM mcp_call_logs
                WHERE token_id = ? AND project_id = ?
                ORDER BY created_at ASC
            """)
            .param(tokenId)
            .param(projectId)
            .query(String.class)
            .list();

            boolean hasSave = false;
            boolean hasRetrieveBeforeSave = false;
            boolean sawRetrieve = false;

            for (String tool : orderedTools) {
                if (retrieveTools.contains(tool)) {
                    sawRetrieve = true;
                }
                if (saveTools.contains(tool)) {
                    hasSave = true;
                    if (sawRetrieve) {
                        hasRetrieveBeforeSave = true;
                    }
                    break;
                }
            }

            if (hasSave) {
                sessionsWithSave++;
                if (hasRetrieveBeforeSave) {
                    sessionsWithRetrieveBeforeSave++;
                }
            } else {
                // If the session only retrieved or didn't save, count retrieve-before-save as true if retrieve occurred
                if (sawRetrieve) {
                    sessionsWithRetrieveBeforeSave++;
                }
            }
        }

        double retrieveBeforeSaveRate = sessionsTracked > 0
            ? Math.round((sessionsWithRetrieveBeforeSave * 100.0 / sessionsTracked) * 10.0) / 10.0
            : 100.0;
        double saveRate = sessionsTracked > 0
            ? Math.round((sessionsWithSave * 100.0 / sessionsTracked) * 10.0) / 10.0
            : 100.0;

        return new McpReliabilityReportResponse(
            totalCalls,
            successfulCalls,
            failedCalls,
            sessionsTracked,
            retrieveBeforeSaveRate,
            saveRate,
            topTools,
            platformDist
        );
    }
}
