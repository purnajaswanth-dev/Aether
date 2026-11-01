package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.TestExecutionRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TestExecutionRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<TestExecutionRecord> rowMapper;

    public TestExecutionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = (rs, rowNum) -> new TestExecutionRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("session_id", UUID.class),
                rs.getString("test_type"),
                rs.getString("framework"),
                rs.getBoolean("passed"),
                rs.getInt("total_tests"),
                rs.getInt("passed_tests"),
                rs.getInt("failed_tests"),
                rs.getLong("duration_ms"),
                rs.getString("error_summary"),
                rs.getObject("log_artifact_id", UUID.class),
                rs.getString("recorded_by"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
        );
    }

    public TestExecutionRecord save(TestExecutionRecord record) {
        UUID id = record.id() != null ? record.id() : UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO test_executions (id, project_id, task_id, session_id, test_type, framework, passed, total_tests, passed_tests, failed_tests, duration_ms, error_summary, log_artifact_id, recorded_by, created_at)
                VALUES (:id, :projectId, :taskId, :sessionId, :testType, :framework, :passed, :totalTests, :passedTests, :failedTests, :durationMs, :errorSummary, :logArtifactId, :recordedBy, NOW())
                """)
                .param("id", id)
                .param("projectId", record.projectId())
                .param("taskId", record.taskId())
                .param("sessionId", record.sessionId())
                .param("testType", record.testType())
                .param("framework", record.framework())
                .param("passed", record.passed())
                .param("totalTests", record.totalTests())
                .param("passedTests", record.passedTests())
                .param("failedTests", record.failedTests())
                .param("durationMs", record.durationMs())
                .param("errorSummary", record.errorSummary())
                .param("logArtifactId", record.logArtifactId())
                .param("recordedBy", record.recordedBy())
                .update();

        return new TestExecutionRecord(
                id, record.projectId(), record.taskId(), record.sessionId(),
                record.testType(), record.framework(), record.passed(),
                record.totalTests(), record.passedTests(), record.failedTests(),
                record.durationMs(), record.errorSummary(), record.logArtifactId(),
                record.recordedBy(), Instant.now()
        );
    }

    public List<TestExecutionRecord> findByTaskId(UUID projectId, UUID taskId) {
        return jdbcClient.sql("""
                SELECT * FROM test_executions
                WHERE project_id = :projectId AND task_id = :taskId
                ORDER BY created_at DESC
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(rowMapper)
                .list();
    }

    public boolean hasPassingTestForTask(UUID projectId, UUID taskId) {
        Integer count = jdbcClient.sql("""
                SELECT COUNT(*) FROM test_executions
                WHERE project_id = :projectId AND task_id = :taskId AND passed = TRUE
                """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .query(Integer.class)
                .single();

        return count != null && count > 0;
    }
}

