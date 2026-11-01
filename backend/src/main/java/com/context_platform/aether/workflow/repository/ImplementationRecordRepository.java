package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.ImplementationRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ImplementationRecordRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<ImplementationRecord> rowMapper;

    public ImplementationRecordRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            List<String> files = List.of();
            List<String> issues = List.of();
            try {
                String fJson = rs.getString("modified_files");
                if (fJson != null && !fJson.isBlank()) {
                    files = objectMapper.readValue(fJson, new TypeReference<>() {});
                }
                String iJson = rs.getString("unresolved_issues");
                if (iJson != null && !iJson.isBlank()) {
                    issues = objectMapper.readValue(iJson, new TypeReference<>() {});
                }
            } catch (Exception ignored) {}

            return new ImplementationRecord(
                    rs.getObject("id", UUID.class),
                    rs.getObject("project_id", UUID.class),
                    rs.getObject("task_id", UUID.class),
                    rs.getObject("session_id", UUID.class),
                    rs.getString("agent_platform"),
                    rs.getString("summary"),
                    files,
                    issues,
                    rs.getString("candidate_status"),
                    rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
            );
        };
    }

    public ImplementationRecord save(ImplementationRecord record) {
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();
        String filesJson = "[]";
        String issuesJson = "[]";
        try {
            filesJson = objectMapper.writeValueAsString(record.modifiedFiles() != null ? record.modifiedFiles() : List.of());
            issuesJson = objectMapper.writeValueAsString(record.unresolvedIssues() != null ? record.unresolvedIssues() : List.of());
        } catch (Exception ignored) {}

        jdbcClient.sql("""
                INSERT INTO implementation_records (id, project_id, task_id, session_id, agent_platform, summary, modified_files, unresolved_issues, candidate_status, created_at)
                VALUES (:id, :projectId, :taskId, :sessionId, :agentPlatform, :summary, :modifiedFiles::jsonb, :unresolvedIssues::jsonb, :candidateStatus, NOW())
                """)
                .param("id", id)
                .param("projectId", record.projectId())
                .param("taskId", record.taskId())
                .param("sessionId", record.sessionId())
                .param("agentPlatform", record.agentPlatform())
                .param("summary", record.summary())
                .param("modifiedFiles", filesJson)
                .param("unresolvedIssues", issuesJson)
                .param("candidateStatus", record.candidateStatus() != null ? record.candidateStatus() : "DONE_UNVERIFIED")
                .update();

        return new ImplementationRecord(
                id, record.projectId(), record.taskId(), record.sessionId(),
                record.agentPlatform(), record.summary(), record.modifiedFiles(),
                record.unresolvedIssues(), record.candidateStatus(), Instant.now()
        );
    }

    public List<ImplementationRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM implementation_records
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at DESC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }

    public Optional<ImplementationRecord> findLatestByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM implementation_records
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at DESC
                LIMIT 1
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .optional();
    }
}

