package com.context_platform.aether.extraction.service;

import com.context_platform.aether.extraction.model.DependencyEdge;
import com.context_platform.aether.extraction.model.ExtractedFact;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class KnowledgeGraphService {

    private final DependencyEdgeRepository edgeRepository;

    public KnowledgeGraphService(DependencyEdgeRepository edgeRepository) {
        this.edgeRepository = edgeRepository;
    }

    public void buildGraphFromFacts(UUID projectId, UUID artifactId, String artifactTitle, List<ExtractedFact> facts) {
        if (facts == null || facts.isEmpty()) return;

        List<DependencyEdge> edges = new ArrayList<>();
        for (ExtractedFact fact : facts) {
            String relType = switch (fact.category()) {
                case CONSTRAINT -> "CONSTRAINED_BY";
                case DECISION -> "IMPLEMENTS";
                case DATABASE_DESIGN -> "DEFINES_SCHEMA";
                case API_CONTRACT -> "EXPOSES_CONTRACT";
                case ARCHITECTURE_COMPONENT -> "CONTAINS_COMPONENT";
                default -> "RELATES_TO";
            };

            edges.add(new DependencyEdge(
                UUID.randomUUID(),
                projectId,
                "ARTIFACT",
                artifactId,
                artifactTitle != null ? artifactTitle : "Artifact",
                "FACT",
                fact.id(),
                fact.factKey(),
                relType,
                Map.of("category", fact.category().name(), "quote", fact.exactQuote()),
                Instant.now()
            ));
        }

        edgeRepository.saveAll(edges);
    }

    public List<DependencyEdge> getConnectedGraph(UUID projectId, UUID rootId, int maxDepth) {
        return edgeRepository.findConnectedGraph(projectId, rootId, maxDepth);
    }
}

