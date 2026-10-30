package com.context_platform.aether.decision;

import com.context_platform.aether.security.RlsContextHelper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DecisionRepository {

    private final JdbcClient jdbcClient;
    private final RlsContextHelper rlsContextHelper;

    public DecisionRepository(JdbcClient jdbcClient, RlsContextHelper rlsContextHelper) {
        this.jdbcClient = jdbcClient;
        this.rlsContextHelper = rlsContextHelper;
    }

    public synchronized String nextPublicId(UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        Integer nextNum = jdbcClient.sql("""
            SELECT COALESCE(MAX(CAST(NULLIF(SUBSTRING(public_id FROM 5), '') AS INTEGER)), 0) + 1
            FROM technical_decisions WHERE project_id = ?
        """)
        .param(projectId)
        .query(Integer.class)
        .single();
        return String.format("DEC-%03d", nextNum != null ? nextNum : 1);
    }

    public TechnicalDecision createDecision(
        UUID projectId,
        String publicId,
        String title,
        String state,
        String category
    ) {
        rlsContextHelper.setProjectContext(projectId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcClient.sql("""
            INSERT INTO technical_decisions (id, project_id, public_id, title, state, category, current_version, lock_version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, 0, now(), now())
        """)
        .param(id)
        .param(projectId)
        .param(publicId)
        .param(title)
        .param(state)
        .param(category)
        .update();

        return new TechnicalDecision(id, projectId, publicId, title, state, category, 1, 0, now, now);
    }

    public DecisionVersion appendVersion(
        UUID decisionId,
        UUID projectId,
        int version,
        String state,
        String problemStatement,
        String proposedSolution,
        String rationale,
        String consequences,
        UUID sourceArtifactVersionId,
        String actorId,
        String actorType,
        String reviewNotes
    ) {
        rlsContextHelper.setProjectContext(projectId);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcClient.sql("""
            INSERT INTO decision_versions (
                id, decision_id, project_id, version, state, problem_statement,
                proposed_solution, rationale, consequences, source_artifact_version_id,
                actor_id, actor_type, review_notes, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        """)
        .param(id)
        .param(decisionId)
        .param(projectId)
        .param(version)
        .param(state)
        .param(problemStatement)
        .param(proposedSolution)
        .param(rationale)
        .param(consequences)
        .param(sourceArtifactVersionId)
        .param(actorId)
        .param(actorType)
        .param(reviewNotes)
        .update();

        return new DecisionVersion(
            id, decisionId, projectId, version, state, problemStatement,
            proposedSolution, rationale, consequences, sourceArtifactVersionId,
            actorId, actorType, reviewNotes, now
        );
    }

    public void addEvidence(
        UUID decisionId,
        UUID projectId,
        String evidenceType,
        String description,
        UUID artifactVersionId,
        String createdBy
    ) {
        rlsContextHelper.setProjectContext(projectId);
        jdbcClient.sql("""
            INSERT INTO decision_evidence (id, decision_id, project_id, evidence_type, description, artifact_version_id, created_by, created_at)
            VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now())
        """)
        .param(decisionId)
        .param(projectId)
        .param(evidenceType)
        .param(description)
        .param(artifactVersionId)
        .param(createdBy)
        .update();
    }

    public Optional<TechnicalDecision> findById(UUID decisionId, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, project_id, public_id, title, state, category, current_version, lock_version, created_at, updated_at
            FROM technical_decisions WHERE id = ? AND project_id = ?
        """)
        .param(decisionId)
        .param(projectId)
        .query(TechnicalDecision.class)
        .optional();
    }

    public List<TechnicalDecision> listDecisions(UUID projectId, String state, String category) {
        rlsContextHelper.setProjectContext(projectId);
        var sql = new StringBuilder("""
            SELECT id, project_id, public_id, title, state, category, current_version, lock_version, created_at, updated_at
            FROM technical_decisions
            WHERE project_id = ?
        """);

        if (state != null && !state.isBlank()) {
            sql.append(" AND state = '").append(state).append("'");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = '").append(category).append("'");
        }
        sql.append(" ORDER BY created_at DESC");

        return jdbcClient.sql(sql.toString())
            .param(projectId)
            .query(TechnicalDecision.class)
            .list();
    }

    public List<DecisionVersion> listVersions(UUID decisionId, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, decision_id, project_id, version, state, problem_statement,
                   proposed_solution, rationale, consequences, source_artifact_version_id,
                   actor_id, actor_type, review_notes, created_at
            FROM decision_versions
            WHERE decision_id = ? AND project_id = ?
            ORDER BY version ASC
        """)
        .param(decisionId)
        .param(projectId)
        .query(DecisionVersion.class)
        .list();
    }

    public Optional<DecisionVersion> getVersion(UUID decisionId, int version, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, decision_id, project_id, version, state, problem_statement,
                   proposed_solution, rationale, consequences, source_artifact_version_id,
                   actor_id, actor_type, review_notes, created_at
            FROM decision_versions
            WHERE decision_id = ? AND version = ? AND project_id = ?
        """)
        .param(decisionId)
        .param(version)
        .param(projectId)
        .query(DecisionVersion.class)
        .optional();
    }

    public List<DecisionEvidence> listEvidence(UUID decisionId, UUID projectId) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            SELECT id, decision_id, project_id, evidence_type, description, artifact_version_id, created_by, created_at
            FROM decision_evidence
            WHERE decision_id = ? AND project_id = ?
            ORDER BY created_at ASC
        """)
        .param(decisionId)
        .param(projectId)
        .query(DecisionEvidence.class)
        .list();
    }

    public int updateStateWithOptimisticLock(UUID decisionId, UUID projectId, String newState, int newVersion, int expectedLockVersion) {
        rlsContextHelper.setProjectContext(projectId);
        return jdbcClient.sql("""
            UPDATE technical_decisions
            SET state = ?, current_version = ?, lock_version = lock_version + 1, updated_at = now()
            WHERE id = ? AND project_id = ? AND lock_version = ?
        """)
        .param(newState)
        .param(newVersion)
        .param(decisionId)
        .param(projectId)
        .param(expectedLockVersion)
        .update();
    }
}
