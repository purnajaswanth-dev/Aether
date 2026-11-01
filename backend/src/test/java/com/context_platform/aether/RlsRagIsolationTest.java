package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.service.FactRepository;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.service.ArtifactChunkRepository;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.retrieval.model.SearchResultItem;
import com.context_platform.aether.retrieval.service.HybridRetrievalEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Phase 3: RLS Isolation for Chunks, Facts, and Embeddings")
public class RlsRagIsolationTest extends BaseTest {

    @Autowired
    private HybridRetrievalEngine retrievalEngine;

    @Autowired
    private ArtifactChunkRepository chunkRepository;

    @Autowired
    private FactRepository factRepository;

    @Test
    @DisplayName("Gate Test: Project A's chunks, facts, and embeddings are completely isolated from Project B")
    void testTenantIsolationOnRagTables() {
        AuthResponse userA = registerUser("alice@tenant-a.com", "SecretPass123!", "Alice");
        AuthResponse userB = registerUser("bob@tenant-b.com", "SecretPass123!", "Bob");

        ProjectResponse projectA = createTestProject("Project Alpha (Confidential)", userA.user().id());
        ProjectResponse projectB = createTestProject("Project Beta (Public)", userB.user().id());

        ActorContext actorA = ActorContext.human(userA.user().id(), userA.user().email());
        ActorContext actorB = ActorContext.human(userB.user().id(), userB.user().email());

        // Ingest top secret specification in Project A
        String secretAlphaSpec = """
            # Project Alpha Secret Specification
            Confidential proprietary algorithm: SuperQuantumAlpha42.
            Must never be leaked or shared with Project Beta.
            """;

        ArtifactResponse artifactA = artifactService.createArtifact(
            projectA.id(),
            new CreateArtifactRequest("Alpha Architecture", "SPECIFICATION", "AETHER", null, secretAlphaSpec, "Top secret spec"),
            actorA
        );

        // Verify chunks and facts exist in Project A
        List<ArtifactChunk> chunksA = chunkRepository.listByArtifact(projectA.id(), artifactA.id(), 1);
        assertFalse(chunksA.isEmpty());

        List<ExtractedFact> factsA = factRepository.listByArtifact(projectA.id(), artifactA.id(), 1);
        assertFalse(factsA.isEmpty());

        // Now, switch context to Project B and search for "SuperQuantumAlpha42"
        List<SearchResultItem> resultsInProjectB = retrievalEngine.search(projectB.id(), "SuperQuantumAlpha42", 10);
        assertTrue(resultsInProjectB.isEmpty(), "Cross-project search must return 0 results; Project B must never see Project A's content");

        // Verify direct repository query under Project B's scope returns 0 rows
        List<ArtifactChunk> crossChunks = chunkRepository.listByArtifact(projectB.id(), artifactA.id(), 1);
        assertTrue(crossChunks.isEmpty(), "Cross-tenant chunk lookup must return 0 rows");

        List<ExtractedFact> crossFacts = factRepository.listByArtifact(projectB.id(), artifactA.id(), 1);
        assertTrue(crossFacts.isEmpty(), "Cross-tenant fact lookup must return 0 rows");
    }
}
