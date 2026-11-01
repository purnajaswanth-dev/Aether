package com.context_platform.aether.retrieval;

import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.ArtifactVersion;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.model.FactCategory;
import com.context_platform.aether.extraction.model.ValidationState;
import com.context_platform.aether.extraction.service.FactRepository;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.IngestionJob;
import com.context_platform.aether.ingestion.model.SecretFinding;
import com.context_platform.aether.ingestion.service.ArtifactChunkRepository;
import com.context_platform.aether.ingestion.service.IngestionJobRepository;
import com.context_platform.aether.ingestion.service.IngestionService;
import com.context_platform.aether.ingestion.service.SecretFindingRepository;
import com.context_platform.aether.mcp.service.McpToolRegistry;
import com.context_platform.aether.orchestrator.model.ContextPackageResponse;
import com.context_platform.aether.orchestrator.service.ContextOrchestratorService;
import com.context_platform.aether.retrieval.model.SearchResultItem;
import com.context_platform.aether.retrieval.service.HybridRetrievalEngine;
import com.context_platform.aether.security.ProjectSecurityChecker;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class KnowledgeAndSearchController {

    private final HybridRetrievalEngine retrievalEngine;
    private final FactRepository factRepository;
    private final IngestionJobRepository jobRepository;
    private final IngestionService ingestionService;
    private final SecretFindingRepository secretRepository;
    private final ArtifactChunkRepository chunkRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final ContextOrchestratorService orchestratorService;
    private final ProjectSecurityChecker securityChecker;

    public KnowledgeAndSearchController(
        HybridRetrievalEngine retrievalEngine,
        FactRepository factRepository,
        IngestionJobRepository jobRepository,
        IngestionService ingestionService,
        SecretFindingRepository secretRepository,
        ArtifactChunkRepository chunkRepository,
        ArtifactRepository artifactRepository,
        ArtifactService artifactService,
        ContextOrchestratorService orchestratorService,
        ProjectSecurityChecker securityChecker
    ) {
        this.retrievalEngine = retrievalEngine;
        this.factRepository = factRepository;
        this.jobRepository = jobRepository;
        this.ingestionService = ingestionService;
        this.secretRepository = secretRepository;
        this.chunkRepository = chunkRepository;
        this.artifactRepository = artifactRepository;
        this.artifactService = artifactService;
        this.orchestratorService = orchestratorService;
        this.securityChecker = securityChecker;
    }

    @GetMapping("/memory/search")
    public List<SearchResultItem> searchMemory(
        @PathVariable UUID projectId,
        @RequestParam String query,
        @RequestParam(defaultValue = "15") int limit
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "VIEWER");
        return retrievalEngine.search(projectId, query, limit);
    }

    @GetMapping("/memory/facts")
    public List<ExtractedFact> listFacts(
        @PathVariable UUID projectId,
        @RequestParam(required = false) FactCategory category,
        @RequestParam(required = false) ValidationState state,
        @RequestParam(defaultValue = "50") int limit
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "VIEWER");
        return factRepository.listByProject(projectId, category, state, limit);
    }

    @PostMapping("/memory/facts/{factId}/review")
    public ExtractedFact reviewFact(
        @PathVariable UUID projectId,
        @PathVariable UUID factId,
        @RequestBody Map<String, String> body
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "MEMBER");

        String stateStr = body.get("validation_state");
        String notes = body.get("notes");
        if (stateStr == null) {
            throw new ApiException("validation_state is required", HttpStatus.BAD_REQUEST);
        }

        ValidationState newState = ValidationState.valueOf(stateStr.toUpperCase());
        factRepository.updateValidationState(factId, newState, actor.userId(), notes);
        return factRepository.findById(factId)
            .orElseThrow(() -> new ApiException("Fact not found: " + factId, HttpStatus.NOT_FOUND));
    }

    @GetMapping("/memory/chunks/{chunkId}/expand")
    public Map<String, Object> expandChunk(
        @PathVariable UUID projectId,
        @PathVariable UUID chunkId
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "VIEWER");

        ArtifactChunk chunk = chunkRepository.findById(chunkId)
            .orElseThrow(() -> new ApiException("Chunk not found: " + chunkId, HttpStatus.NOT_FOUND));

        if (!chunk.projectId().equals(projectId)) {
            throw new ApiException("Chunk does not belong to this project", HttpStatus.FORBIDDEN);
        }

        ArtifactVersion version = artifactRepository.getVersion(chunk.artifactId(), chunk.artifactVersion(), projectId)
            .orElseThrow(() -> new ApiException("Artifact version not found", HttpStatus.NOT_FOUND));

        String rawOriginal = artifactService.resolveContentAndVerifyHash(projectId, version);

        String highlightedView = McpToolRegistry.formatUntrustedData(
            chunk.artifactId().toString(),
            chunk.artifactVersion(),
            chunk.contentHash(),
            "AETHER_EXPANDED_CHUNK",
            """
            [SECTION PATH: %s]
            [LINES %d - %d, OFFSETS %d - %d]
            <<<CHUNK_SPAN>>>
            %s
            <<<END_CHUNK_SPAN>>>

            --- FULL ORIGINAL ARTIFACT CONTENT ---
            %s
            """.formatted(
                chunk.parentSectionPath(),
                chunk.startLine(), chunk.endLine(),
                chunk.startOffset(), chunk.endOffset(),
                chunk.content(),
                rawOriginal
            )
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("chunk_id", chunk.id().toString());
        resp.put("artifact_id", chunk.artifactId().toString());
        resp.put("version", chunk.artifactVersion());
        resp.put("parent_section_path", chunk.parentSectionPath());
        resp.put("chunk_type", chunk.chunkType().name());
        resp.put("start_line", chunk.startLine());
        resp.put("end_line", chunk.endLine());
        resp.put("start_offset", chunk.startOffset());
        resp.put("end_offset", chunk.endOffset());
        resp.put("content", chunk.content());
        resp.put("expanded_view", highlightedView);
        return resp;
    }

    @GetMapping("/memory/ingestion-jobs")
    public List<IngestionJob> listIngestionJobs(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "20") int limit
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "VIEWER");
        return jobRepository.listRecent(projectId, limit);
    }

    @PostMapping("/memory/ingestion-jobs/{jobId}/retry")
    public IngestionJob retryJob(
        @PathVariable UUID projectId,
        @PathVariable UUID jobId
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "MEMBER");
        return ingestionService.retryIngestion(projectId, jobId);
    }

    @GetMapping("/memory/secrets")
    public List<SecretFinding> listSecrets(
        @PathVariable UUID projectId
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "VIEWER");
        return secretRepository.listByProject(projectId);
    }

    @PostMapping("/orchestrator/preview")
    public ContextPackageResponse previewContextPackage(
        @PathVariable UUID projectId,
        @RequestBody Map<String, Object> body
    ) {
        ActorContext actor = securityChecker.getCurrentActor();
        securityChecker.verifyProjectAccess(projectId, actor.userId(), "VIEWER");

        String task = (String) body.get("task");
        if (task == null || task.isBlank()) {
            throw new ApiException("Task description is required", HttpStatus.BAD_REQUEST);
        }

        String targetModel = (String) body.get("target_model");
        Integer budget = body.get("token_budget") instanceof Number num ? num.intValue() : null;

        return orchestratorService.buildContextPackage(
            projectId,
            "WEB_PREVIEW",
            task,
            targetModel,
            budget
        );
    }
}
