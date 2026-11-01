package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.artifact.CreateVersionRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionResponse;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.retrieval.model.SearchResultItem;
import com.context_platform.aether.retrieval.service.HybridRetrievalEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3: Hybrid Retrieval & Version Staleness Tests")
public class HybridRetrievalAndStalenessTest extends BaseTest {

    @Autowired
    private HybridRetrievalEngine retrievalEngine;

    @Test
    @DisplayName("Gate Test: Current approved decision outranks historical/superseded material; superseded is never returned as current")
    void testCurrentApprovedOutranksSuperseded() {
        AuthResponse user = registerUser("architect@aether.test", "StrongPass123!", "Lead Architect");
        ProjectResponse project = createTestProject("Aether Core Engine", user.user().id());

        ActorContext actor = ActorContext.human(user.user().id(), user.user().email());

        // Step 1: Create Architecture v1 proposing MySQL
        String archV1 = """
            # Architecture Specification
            ## Database Strategy
            We will use MySQL 8.0 for primary database storage.
            Constraints: Enforce foreign keys and use InnoDB engine.
            """;
        ArtifactResponse artifact = artifactService.createArtifact(
            project.id(),
            new CreateArtifactRequest("System Architecture", "ARCHITECTURE", "CLAUDE", null, archV1, "Initial draft with MySQL"),
            actor
        );

        // Step 2: Propose Decision 1 (MySQL)
        DecisionResponse dec1 = decisionService.createDecision(
            project.id(),
            new CreateDecisionRequest("Adopt MySQL Database", "DATABASE", "Need persistent storage", "Adopt MySQL 8.0", "Legacy experience", "None", null),
            user.user().id()
        );

        // Step 3: Architecture changes! Team creates Decision 2 (PostgreSQL 16) and approves it
        DecisionResponse dec2 = decisionService.createDecision(
            project.id(),
            new CreateDecisionRequest("Adopt PostgreSQL 16 with pgvector", "DATABASE", "Need vector similarity and RLS", "Adopt PostgreSQL 16", "Native pgvector and robust RLS", "Requires pgvector extension", null),
            user.user().id()
        );

        // Human architect moves Decision 2 to UNDER_REVIEW and then APPROVES it
        decisionService.submitForReview(project.id(), dec2.id(), new com.context_platform.aether.decision.DecisionActionRequest(0, "Reviewing PostgreSQL", null, null), user.user().id());
        decisionService.approveDecision(project.id(), dec2.id(), new com.context_platform.aether.decision.DecisionActionRequest(1, "Approved by Architecture Board", null, null), user.user().id());

        // Step 4: Save Architecture v2 with PostgreSQL 16
        String archV2 = """
            # Architecture Specification
            ## Database Strategy
            We adopt PostgreSQL 16 with pgvector for persistent relational data and high-dimensional embeddings.
            Row-Level Security (RLS) is strictly enforced for tenant isolation.
            """;
        artifactService.createVersion(
            project.id(),
            artifact.id(),
            new CreateVersionRequest(archV2, "Updated database to PostgreSQL 16 with pgvector"),
            actor
        );

        // Step 5: Perform Hybrid Search for "database storage strategy"
        List<SearchResultItem> results = retrievalEngine.search(project.id(), "database storage strategy", 10);

        assertFalse(results.isEmpty(), "Search must return results");

        // The top ranking result MUST be the current approved PostgreSQL architecture/decision
        SearchResultItem topResult = results.get(0);
        assertTrue(topResult.content().contains("PostgreSQL"), "Top result must reflect current approved PostgreSQL architecture");
        assertFalse(topResult.isSuperseded(), "Top result must not be superseded");

        // Verify that if any v1 MySQL chunk is returned, it is clearly flagged as SUPERSEDED and ranked below
        for (int i = 0; i < results.size(); i++) {
            SearchResultItem item = results.get(i);
            if (item.content().contains("MySQL") && item.artifactVersion() == 1) {
                assertTrue(item.isSuperseded(), "v1 MySQL chunk must be flagged as isSuperseded=true");
                assertEquals("SUPERSEDED", item.approvalState(), "Superseded chunk must be labeled SUPERSEDED");
                assertTrue(i > 0, "Superseded chunk must rank lower than current approved v2 chunk");
            }
        }
    }
}
