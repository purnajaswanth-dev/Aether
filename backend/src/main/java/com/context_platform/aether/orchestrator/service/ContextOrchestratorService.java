package com.context_platform.aether.orchestrator.service;

import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.decision.DecisionRepository;
import com.context_platform.aether.decision.DecisionState;
import com.context_platform.aether.decision.TechnicalDecision;
import com.context_platform.aether.event.TimelineEvent;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.model.FactCategory;
import com.context_platform.aether.extraction.service.FactRepository;
import com.context_platform.aether.ingestion.model.ChunkType;
import com.context_platform.aether.orchestrator.model.ContextPackageResponse;
import com.context_platform.aether.orchestrator.model.ContextRequestType;
import com.context_platform.aether.orchestrator.model.ModelProfile;
import com.context_platform.aether.orchestrator.model.OmittedItem;
import com.context_platform.aether.orchestrator.repository.ContextPackageRepository;
import com.context_platform.aether.orchestrator.repository.ContextRetrievalLogRepository;
import com.context_platform.aether.orchestrator.repository.ModelProfileRepository;
import com.context_platform.aether.project.Project;
import com.context_platform.aether.project.ProjectRepository;
import com.context_platform.aether.retrieval.model.SearchResultItem;
import com.context_platform.aether.retrieval.service.HybridRetrievalEngine;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ContextOrchestratorService {

    private final RequestClassifier classifier;
    private final ModelProfileRepository profileRepository;
    private final TokenBudgetManager budgetManager;
    private final HybridRetrievalEngine retrievalEngine;
    private final ProjectRepository projectRepository;
    private final DecisionRepository decisionRepository;
    private final FactRepository factRepository;
    private final TimelineService timelineService;
    private final ContextPackageRepository packageRepository;
    private final ContextRetrievalLogRepository retrievalLogRepository;

    public ContextOrchestratorService(
        RequestClassifier classifier,
        ModelProfileRepository profileRepository,
        TokenBudgetManager budgetManager,
        HybridRetrievalEngine retrievalEngine,
        ProjectRepository projectRepository,
        DecisionRepository decisionRepository,
        FactRepository factRepository,
        TimelineService timelineService,
        ContextPackageRepository packageRepository,
        ContextRetrievalLogRepository retrievalLogRepository
    ) {
        this.classifier = classifier;
        this.profileRepository = profileRepository;
        this.budgetManager = budgetManager;
        this.retrievalEngine = retrievalEngine;
        this.projectRepository = projectRepository;
        this.decisionRepository = decisionRepository;
        this.factRepository = factRepository;
        this.timelineService = timelineService;
        this.packageRepository = packageRepository;
        this.retrievalLogRepository = retrievalLogRepository;
    }

    public ContextPackageResponse buildContextPackage(
        UUID projectId,
        String clientPlatform,
        String taskDescription,
        String targetModel,
        Integer requestedBudget
    ) {
        return buildContextPackage(projectId, clientPlatform, taskDescription, targetModel, requestedBudget, com.context_platform.aether.workflow.model.OrchestratorMode.DEFAULT, null);
    }

    public ContextPackageResponse buildContextPackage(
        UUID projectId,
        String clientPlatform,
        String taskDescription,
        String targetModel,
        Integer requestedBudget,
        com.context_platform.aether.workflow.model.OrchestratorMode mode,
        UUID taskId
    ) {
        UUID packageId = UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Classify Request
        ContextRequestType requestType = classifier.classify(taskDescription);

        // 2. Identify Project
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // 3. Model Profile & Budget calculation
        ModelProfile profile = profileRepository.findByModelId(targetModel);
        int effectiveBudget = budgetManager.calculateEffectiveBudget(profile, requestedBudget);

        // 4. Hybrid Retrieval
        long startTime = System.currentTimeMillis();
        List<SearchResultItem> retrievedItems = retrievalEngine.search(projectId, taskDescription, 20);
        long duration = System.currentTimeMillis() - startTime;

        // 5. Fetch Approved Decisions (Rule 6: Approved beats historical)
        List<TechnicalDecision> approvedDecisions = decisionRepository.listDecisions(projectId, "APPROVED", null);

        // 6. Fetch Key Constraints & Knowledge Facts
        List<ExtractedFact> constraints = factRepository.listByProject(projectId, FactCategory.CONSTRAINT, null, 10);

        // 7. Dynamic Level Filtering based on Request Type
        // Architecture Review: L1 Overview, L2 Architecture, Constraints, Decisions, Findings. Exclude deployment logs and source files.
        // Debugging: L4 Task memory, latest implementation state, relevant logs, config decisions, recent changes. Exclude unrelated history.
        List<SearchResultItem> filteredItems = new ArrayList<>();
        for (SearchResultItem item : retrievedItems) {
            if (requestType == ContextRequestType.ARCHITECTURE_REVIEW) {
                if (item.chunkType() == ChunkType.LOG_ENTRY || item.chunkType() == ChunkType.JAVA_METHOD) {
                    continue; // Exclude implementation logs/methods from high-level architecture review
                }
            } else if (requestType == ContextRequestType.DEBUGGING) {
                if (item.isSuperseded() && item.chunkType() != ChunkType.LOG_ENTRY) {
                    continue; // Exclude historical artifacts from debugging
                }
            }
            filteredItems.add(item);
        }

        // 8. Token Budget Packing & Omitted Items Indexing
        int currentTokens = 0;
        List<Map<String, Object>> includedChunks = new ArrayList<>();
        List<OmittedItem> omittedItems = new ArrayList<>();

        StringBuilder formattedBuilder = new StringBuilder();
        formattedBuilder.append("=== AETHER CONTEXT PACKAGE ===\n");
        formattedBuilder.append("Project: ").append(project.name()).append(" [").append(project.publicId()).append("]\n");
        formattedBuilder.append("Request Type: ").append(requestType).append("\n");
        formattedBuilder.append("Orchestrator Mode: ").append(mode != null ? mode.name() : "DEFAULT").append("\n");
        if (taskId != null) {
            formattedBuilder.append("Active Task ID: ").append(taskId).append("\n");
        }
        formattedBuilder.append("Target Model: ").append(profile.modelId()).append("\n");
        formattedBuilder.append("Token Budget: ").append(effectiveBudget).append("\n\n");

        // L1: Project Overview
        String overviewSection = """
            ## L1: Project Overview
            - Public ID: %s
            - Description: %s
            - Tech Stack: %s
            """.formatted(project.publicId(), project.description(), project.techStack());
        currentTokens += budgetManager.estimateTokens(overviewSection);
        formattedBuilder.append(overviewSection).append("\n");

        // L2: Approved Decisions
        if (!approvedDecisions.isEmpty()) {
            StringBuilder decBuilder = new StringBuilder("## L2: Approved Decisions\n");
            for (TechnicalDecision dec : approvedDecisions) {
                String decLine = "- [" + dec.publicId() + "] " + dec.title() + " (Category: " + dec.category() + ", State: " + dec.state() + ")\n";
                int decTokens = budgetManager.estimateTokens(decLine);
                if (currentTokens + decTokens < effectiveBudget) {
                    decBuilder.append(decLine);
                    currentTokens += decTokens;
                } else {
                    omittedItems.add(new OmittedItem(
                        dec.id().toString(), dec.title(), "Exceeded token budget", decTokens,
                        "get_decisions(decision_id=\"" + dec.id() + "\")"
                    ));
                }
            }
            formattedBuilder.append(decBuilder).append("\n");
        }

        // L3 & L4: Retrieved Chunks & Findings
        formattedBuilder.append("## L3/L4: Retrieved Project Knowledge & Artifacts\n");
        for (SearchResultItem item : filteredItems) {
            String chunkHeader = "### " + item.parentSectionPath() + " [Version: " + item.artifactVersion() + ", State: " + item.approvalState() + "]\n";
            String fullChunkText = chunkHeader + item.untrustedFormattedContent() + "\n\n";
            int chunkTokens = budgetManager.estimateTokens(fullChunkText);

            if (currentTokens + chunkTokens < effectiveBudget) {
                currentTokens += chunkTokens;
                formattedBuilder.append(fullChunkText);
                includedChunks.add(Map.of(
                    "chunk_id", item.chunkId().toString(),
                    "artifact_id", item.artifactId().toString(),
                    "version", item.artifactVersion(),
                    "path", item.parentSectionPath(),
                    "state", item.approvalState(),
                    "score", item.rrfScore()
                ));
            } else {
                omittedItems.add(new OmittedItem(
                    item.chunkId().toString(),
                    item.parentSectionPath(),
                    "Omitted to preserve token headroom",
                    chunkTokens,
                    "expand_chunk(chunk_id=\"" + item.chunkId() + "\")"
                ));
            }
        }

        // Staged Retrieval Index of Omitted Items
        if (!omittedItems.isEmpty()) {
            formattedBuilder.append("## Staged Retrieval Index (Omitted Items)\n");
            for (OmittedItem omitted : omittedItems) {
                formattedBuilder.append("- OMITTED: ").append(omitted.title())
                    .append(" (~").append(omitted.estimatedTokens()).append(" tokens) -> Fetch: ")
                    .append(omitted.fetchCommand()).append("\n");
            }
        }

        Map<String, Object> sections = Map.of(
            "overview", overviewSection,
            "approved_decisions_count", approvedDecisions.size(),
            "included_chunks", includedChunks,
            "omitted_count", omittedItems.size()
        );

        ContextPackageResponse response = new ContextPackageResponse(
            packageId, projectId, requestType, profile.modelId(),
            effectiveBudget, currentTokens, sections, omittedItems,
            formattedBuilder.toString(), now
        );

        // Persist package metadata
        packageRepository.save(response, taskDescription);
        retrievalLogRepository.log(packageId, projectId, "HYBRID_RRF", taskDescription, retrievedItems.size(), duration);

        return response;
    }
}
