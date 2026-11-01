package com.context_platform.aether.extraction.service;

import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.model.FactCategory;
import com.context_platform.aether.extraction.model.ValidationState;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FactRepository {

    private final JdbcClient jdbcClient;

    public FactRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(ExtractedFact fact) {
        jdbcClient.sql("""
            INSERT INTO extracted_facts (
                id, project_id, artifact_id, artifact_version, category,
                fact_key, fact_statement, exact_quote, start_offset, end_offset,
                source_platform, extraction_method, validation_state, validation_notes,
                approval_state, confidence_score, reviewed_by, reviewed_at, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                validation_state = EXCLUDED.validation_state,
                validation_notes = EXCLUDED.validation_notes,
                reviewed_by = EXCLUDED.reviewed_by,
                reviewed_at = EXCLUDED.reviewed_at
            """)
            .params(
                fact.id(),
                fact.projectId(),
                fact.artifactId(),
                fact.artifactVersion(),
                fact.category().name(),
                fact.factKey(),
                fact.factStatement(),
                fact.exactQuote(),
                fact.startOffset(),
                fact.endOffset(),
                fact.sourcePlatform(),
                fact.extractionMethod(),
                fact.validationState().name(),
                fact.validationNotes(),
                fact.approvalState(),
                fact.confidenceScore(),
                fact.reviewedBy(),
                fact.reviewedAt() != null ? Timestamp.from(fact.reviewedAt()) : null,
                Timestamp.from(fact.createdAt())
            )
            .update();
    }

    public void saveAll(List<ExtractedFact> facts) {
        for (ExtractedFact fact : facts) {
            save(fact);
        }
    }

    public Optional<ExtractedFact> findById(UUID factId) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, category,
                   fact_key, fact_statement, exact_quote, start_offset, end_offset,
                   source_platform, extraction_method, validation_state, validation_notes,
                   approval_state, confidence_score, reviewed_by, reviewed_at, created_at
            FROM extracted_facts
            WHERE id = ?
            """)
            .param(factId)
            .query((rs, rowNum) -> new ExtractedFact(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                FactCategory.valueOf(rs.getString("category")),
                rs.getString("fact_key"),
                rs.getString("fact_statement"),
                rs.getString("exact_quote"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getString("source_platform"),
                rs.getString("extraction_method"),
                ValidationState.valueOf(rs.getString("validation_state")),
                rs.getString("validation_notes"),
                rs.getString("approval_state"),
                rs.getDouble("confidence_score"),
                rs.getObject("reviewed_by", UUID.class),
                rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
            ))
            .optional();
    }

    public List<ExtractedFact> listByProject(UUID projectId, FactCategory category, ValidationState state, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, project_id, artifact_id, artifact_version, category,
                   fact_key, fact_statement, exact_quote, start_offset, end_offset,
                   source_platform, extraction_method, validation_state, validation_notes,
                   approval_state, confidence_score, reviewed_by, reviewed_at, created_at
            FROM extracted_facts
            WHERE project_id = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(projectId);

        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category.name());
        }
        if (state != null) {
            sql.append(" AND validation_state = ?");
            params.add(state.name());
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(limit);

        return jdbcClient.sql(sql.toString())
            .params(params)
            .query((rs, rowNum) -> new ExtractedFact(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                FactCategory.valueOf(rs.getString("category")),
                rs.getString("fact_key"),
                rs.getString("fact_statement"),
                rs.getString("exact_quote"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getString("source_platform"),
                rs.getString("extraction_method"),
                ValidationState.valueOf(rs.getString("validation_state")),
                rs.getString("validation_notes"),
                rs.getString("approval_state"),
                rs.getDouble("confidence_score"),
                rs.getObject("reviewed_by", UUID.class),
                rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public List<ExtractedFact> listByArtifact(UUID projectId, UUID artifactId, int version) {
        return jdbcClient.sql("""
            SELECT id, project_id, artifact_id, artifact_version, category,
                   fact_key, fact_statement, exact_quote, start_offset, end_offset,
                   source_platform, extraction_method, validation_state, validation_notes,
                   approval_state, confidence_score, reviewed_by, reviewed_at, created_at
            FROM extracted_facts
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            ORDER BY start_offset ASC
            """)
            .params(projectId, artifactId, version)
            .query((rs, rowNum) -> new ExtractedFact(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                FactCategory.valueOf(rs.getString("category")),
                rs.getString("fact_key"),
                rs.getString("fact_statement"),
                rs.getString("exact_quote"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getString("source_platform"),
                rs.getString("extraction_method"),
                ValidationState.valueOf(rs.getString("validation_state")),
                rs.getString("validation_notes"),
                rs.getString("approval_state"),
                rs.getDouble("confidence_score"),
                rs.getObject("reviewed_by", UUID.class),
                rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
            ))
            .list();
    }

    public void updateValidationState(UUID factId, ValidationState newState, UUID reviewerId, String notes) {
        jdbcClient.sql("""
            UPDATE extracted_facts
            SET validation_state = ?, reviewed_by = ?, reviewed_at = ?, validation_notes = ?
            WHERE id = ?
            """)
            .params(newState.name(), reviewerId, Timestamp.from(Instant.now()), notes, factId)
            .update();
    }

    public void deleteByArtifactVersion(UUID projectId, UUID artifactId, int version) {
        jdbcClient.sql("""
            DELETE FROM extracted_facts
            WHERE project_id = ? AND artifact_id = ? AND artifact_version = ?
            """)
            .params(projectId, artifactId, version)
            .update();
    }
}

