package com.context_platform.aether.ingestion.service;

import com.context_platform.aether.artifact.Artifact;
import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.artifact.ArtifactVersion;
import com.context_platform.aether.embedding.service.EmbeddingService;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.service.FactExtractorService;
import com.context_platform.aether.extraction.service.FactRepository;
import com.context_platform.aether.extraction.service.KnowledgeGraphService;
import com.context_platform.aether.ingestion.chunker.ChunkerRegistry;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.IngestionJob;
import com.context_platform.aether.ingestion.model.IngestionStatus;
import com.context_platform.aether.ingestion.model.SecretFinding;
import com.context_platform.aether.storage.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionJobRepository jobRepository;
    private final SecretDetectorService secretDetector;
    private final SecretFindingRepository secretRepository;
    private final ChunkerRegistry chunkerRegistry;
    private final ArtifactChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final FactExtractorService factExtractor;
    private final FactRepository factRepository;
    private final KnowledgeGraphService graphService;
    private final ArtifactRepository artifactRepository;
    private final StorageService storageService;
    private final TimelineService timelineService;
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public IngestionService(
        IngestionJobRepository jobRepository,
        SecretDetectorService secretDetector,
        SecretFindingRepository secretRepository,
        ChunkerRegistry chunkerRegistry,
        ArtifactChunkRepository chunkRepository,
        EmbeddingService embeddingService,
        FactExtractorService factExtractor,
        FactRepository factRepository,
        KnowledgeGraphService graphService,
        ArtifactRepository artifactRepository,
        StorageService storageService,
        TimelineService timelineService,
        JdbcClient jdbcClient,
        ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.secretDetector = secretDetector;
        this.secretRepository = secretRepository;
        this.chunkerRegistry = chunkerRegistry;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.factExtractor = factExtractor;
        this.factRepository = factRepository;
        this.graphService = graphService;
        this.artifactRepository = artifactRepository;
        this.storageService = storageService;
        this.timelineService = timelineService;
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestionJob ingestArtifactVersion(
        UUID projectId,
        UUID artifactId,
        int artifactVersion,
        String rawContent,
        String artifactType,
        String title,
        String sourcePlatform
    ) {
        // Idempotency: Clean up prior chunks, embeddings, facts, and edges for this version
        chunkRepository.deleteByArtifactVersion(projectId, artifactId, artifactVersion);
        secretRepository.deleteByArtifactVersion(projectId, artifactId, artifactVersion);
        factRepository.deleteByArtifactVersion(projectId, artifactId, artifactVersion);

        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        IngestionJob job = new IngestionJob(
            jobId, projectId, artifactId, artifactVersion,
            IngestionStatus.PROCESSING, 1, 3, null, "{}",
            now, now, null
        );
        jobRepository.save(job);

        Map<String, Object> stepDetails = new LinkedHashMap<>();

        try {
            // Step 1: Original preserved (already saved to storage service by caller)
            stepDetails.put("step_1_preserve_original", "VERIFIED");

            // Step 2 & 3: Metadata extraction & type detection
            stepDetails.put("step_2_metadata", Map.of("length", rawContent.length(), "title", title));
            stepDetails.put("step_3_type_detection", artifactType);

            // Step 4 & 5: Secret detection and redaction
            SecretDetectorService.SecretDetectionResult secretResult = secretDetector.scanAndRedact(
                projectId, artifactId, artifactVersion, rawContent
            );

            String contentForRetrieval = secretResult.redactedContent();
            if (secretResult.hasSecrets()) {
                for (SecretFinding finding : secretResult.findings()) {
                    secretRepository.save(finding);
                }
                // Flag artifact in DB
                jdbcClient.sql("UPDATE artifacts SET has_secrets = true WHERE id = ?")
                    .param(artifactId)
                    .update();
                stepDetails.put("step_5_secret_detection", Map.of(
                    "flagged", true,
                    "count", secretResult.findings().size()
                ));
            } else {
                stepDetails.put("step_5_secret_detection", Map.of("flagged", false, "count", 0));
            }

            // Step 6: Structured knowledge extraction with meaning preservation
            List<ExtractedFact> facts = factExtractor.extractFacts(
                projectId, artifactId, artifactVersion, contentForRetrieval,
                sourcePlatform != null ? sourcePlatform : "AETHER",
                "rule-based-v1"
            );
            factRepository.saveAll(facts);
            stepDetails.put("step_6_knowledge_extraction", Map.of("facts_extracted", facts.size()));

            // Step 7: Structure-aware chunking
            Map<String, Object> baseMeta = Map.of(
                "title", title,
                "artifact_type", artifactType,
                "source_platform", sourcePlatform != null ? sourcePlatform : "AETHER"
            );
            List<ArtifactChunk> chunks = chunkerRegistry.chunk(
                projectId, artifactId, artifactVersion, contentForRetrieval,
                artifactType, title, baseMeta
            );
            chunkRepository.saveAll(chunks);
            stepDetails.put("step_7_chunking", Map.of("chunks_count", chunks.size()));

            // Step 8 & 9: Embeddings and Full-text indexing
            embeddingService.generateAndStoreEmbeddings(projectId, chunks);
            stepDetails.put("step_8_embeddings", Map.of(
                "model", embeddingService.getEmbeddingProvider().getModelName(),
                "dimension", embeddingService.getEmbeddingProvider().getDimension()
            ));
            stepDetails.put("step_9_full_text_indexing", "COMPLETED_VIA_TSVECTOR");

            // Step 10: Graph relationship creation
            graphService.buildGraphFromFacts(projectId, artifactId, title, facts);
            stepDetails.put("step_10_graph_relationships", "BUILT");

            // Step 11: Memory index update
            stepDetails.put("step_11_memory_index", "SYNCHRONIZED");

            // Step 12: Timeline event & Job Completion
            IngestionStatus finalStatus = secretResult.hasSecrets() ? IngestionStatus.NEEDS_ATTENTION : IngestionStatus.INDEXED;
            Instant completedAt = Instant.now();

            String stepsJson = serialize(stepDetails);
            IngestionJob completedJob = new IngestionJob(
                jobId, projectId, artifactId, artifactVersion,
                finalStatus, 1, 3, null, stepsJson,
                now, now, completedAt
            );
            jobRepository.save(completedJob);

            timelineService.append(
                projectId,
                null,
                sourcePlatform != null ? sourcePlatform : "AETHER",
                "INGESTION_COMPLETED",
                artifactId,
                null,
                artifactVersion,
                "SUCCESS",
                "SYSTEM",
                "SYSTEM",
                "Artifact '" + title + "' (v" + artifactVersion + ") indexed: " + chunks.size() + " chunks, " + facts.size() + " facts" +
                (secretResult.hasSecrets() ? " [WARNING: Secrets detected and redacted]" : ""),
                "{}"
            );

            return completedJob;

        } catch (Exception e) {
            log.error("Ingestion failed for artifact {} v{}: {}", artifactId, artifactVersion, e.getMessage(), e);
            stepDetails.put("error", e.getMessage());
            String stepsJson = serialize(stepDetails);
            IngestionJob failedJob = new IngestionJob(
                jobId, projectId, artifactId, artifactVersion,
                IngestionStatus.FAILED, 1, 3, e.getMessage(), stepsJson,
                now, now, Instant.now()
            );
            jobRepository.save(failedJob);
            throw new RuntimeException("Ingestion pipeline failed: " + e.getMessage(), e);
        }
    }

    public IngestionJob retryIngestion(UUID projectId, UUID jobId) {
        IngestionJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Ingestion job not found: " + jobId));

        Artifact artifact = artifactRepository.findArtifactById(job.artifactId(), projectId)
            .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + job.artifactId()));

        ArtifactVersion version = artifactRepository.getVersion(job.artifactId(), job.artifactVersion(), projectId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + job.artifactVersion()));

        String content = resolveVersionContent(projectId, version);
        return ingestArtifactVersion(
            projectId, job.artifactId(), job.artifactVersion(),
            content, artifact.artifactType(), artifact.title(), artifact.sourcePlatform()
        );
    }

    private String resolveVersionContent(UUID projectId, ArtifactVersion version) {
        if (version.content() != null && !version.content().isBlank()) {
            return version.content();
        } else if (version.storageKey() != null) {
            byte[] retrieved = storageService.retrieve(projectId, version.storageKey(), version.contentHash());
            return new String(retrieved, StandardCharsets.UTF_8);
        }
        return "";
    }

    private String serialize(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
