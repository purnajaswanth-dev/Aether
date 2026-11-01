package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.ingestion.service.ArtifactChunkRepository;
import com.context_platform.aether.ingestion.service.IngestionService;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Phase 3: Idempotent Re-Ingestion Tests")
public class IdempotentReingestionTest extends BaseTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private ArtifactChunkRepository chunkRepository;

    @Test
    @DisplayName("Gate Test: Re-ingesting the same artifact creates zero duplicate chunks or facts")
    void testReingestionCreatesNoDuplicates() {
        AuthResponse user = registerUser("idempotency_lead@aether.test", "StrongPass123!", "QA Lead");
        ProjectResponse project = createTestProject("Idempotent Engine", user.user().id());
        ActorContext actor = ActorContext.human(user.user().id(), user.user().email());

        String content = """
            # Microservices Architecture
            ## Service Registry
            Use Eureka or Consul for service discovery.
            ## Gateway
            Spring Cloud Gateway handles routing.
            """;

        ArtifactResponse artifact = artifactService.createArtifact(
            project.id(),
            new CreateArtifactRequest("Microservices Spec", "ARCHITECTURE", "AETHER", null, content, "Initial spec"),
            actor
        );

        int chunksAfterFirstIngest = chunkRepository.listByArtifact(project.id(), artifact.id(), 1).size();
        assertEquals(3, chunksAfterFirstIngest);

        // Re-ingest the exact same artifact version
        ingestionService.ingestArtifactVersion(
            project.id(),
            artifact.id(),
            1,
            content,
            "ARCHITECTURE",
            "Microservices Spec",
            "AETHER"
        );

        int chunksAfterSecondIngest = chunkRepository.listByArtifact(project.id(), artifact.id(), 1).size();
        assertEquals(chunksAfterFirstIngest, chunksAfterSecondIngest, "Re-ingesting must be idempotent and produce no duplicate chunks");
    }
}

