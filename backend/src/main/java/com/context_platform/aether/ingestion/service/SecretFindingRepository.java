package com.context_platform.aether.ingestion.service;

import com.context_platform.aether.ingestion.model.SecretFinding;
import com.context_platform.aether.ingestion.model.SecretFindingType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class SecretFindingRepository {

    private final JdbcClient jdbcClient;

    public SecretFindingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(SecretFinding finding) {
        jdbcClient.sql("""
            INSERT INTO secret_findings (id, project_id, artifact_id, artifact_version, finding_type, redacted_snippet, line_number, start_offset, end_offset, entropy, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """)
            .params(
                finding.id(),
                finding.projectId(),
                finding.artifactId(),
                finding.artifactVersion(),
                finding.findingType().name(),
                finding.redactedSnippet(),
                finding.lineNumber(),
                finding.startOffset(),
                finding.endOffset(),
                finding.entropy(),
                Timestamp.from(finding.createdAt())
            )
            .update();
    }

    public List<SecretFinding> listByArtifact(UUID projectId, UUID artifactId, int version) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, finding_type, redacted_snippet, line_number, start_offset, end_offset, entropy, created_at
            FROM secret_findings
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            ORDER BY line_number ASC
            """)
            .params(projectId, artifactId, version)
            .query((rs, rowNum) -> new SecretFinding(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                SecretFindingType.valueOf(rs.getString("finding_type")),
                rs.getString("redacted_snippet"),
                rs.getInt("line_number"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getDouble("entropy"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public List<SecretFinding> listByProject(UUID projectId) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, finding_type, redacted_snippet, line_number, start_offset, end_offset, entropy, created_at
            FROM secret_findings
            WHERE project_id = ?
            ORDER BY created_at DESC
            """)
            .param(projectId)
            .query((rs, rowNum) -> new SecretFinding(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                SecretFindingType.valueOf(rs.getString("finding_type")),
                rs.getString("redacted_snippet"),
                rs.getInt("line_number"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getDouble("entropy"),
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public void deleteByArtifactVersion(UUID projectId, UUID artifactId, int version) {
        jdbcClient.sql("""
            DELETE FROM secret_findings
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            """)
            .params(projectId, artifactId, version)
            .update();
    }
}

