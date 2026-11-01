package com.context_platform.aether.performance;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.embedding.provider.EmbeddingProvider;
import com.context_platform.aether.orchestrator.model.ContextPackageResponse;
import com.context_platform.aether.orchestrator.service.ContextOrchestratorService;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.retrieval.model.SearchResultItem;
import com.context_platform.aether.retrieval.service.HybridRetrievalEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PerformanceBenchmarkTest extends BaseTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceBenchmarkTest.class);

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private HybridRetrievalEngine retrievalEngine;

    @Autowired
    private ContextOrchestratorService orchestratorService;

    private UUID projectId;
    private UUID userId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("perf_user_" + UUID.randomUUID() + "@example.com", "Password123!", "Perf Tester");
        this.userId = user.user().id();
        this.userEmail = user.user().email();

        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest(
                "Perf Benchmark " + System.currentTimeMillis(),
                "Performance and Scalability Profiling",
                "WEB_APP",
                List.of("Java"),
                "/perf/workspace",
                "LOCAL_FIRST"
            ),
            userId,
            userEmail
        );
        this.projectId = project.id();

        // Seed sample engineering artifacts
        for (int i = 1; i <= 5; i++) {
            String content = """
                # Microservice Component %d: Authentication & Event Streaming
                This service manages distributed session validation and high-throughput Kafka streaming.
                Configuration requires JWT RS256 with 2048-bit key rotation every 30 days.
                Database connections use HikariCP pool sized at 20 max connections with 30s connection timeout.
                The service implements exponential backoff retry for network disconnections with max 5 attempts.
                Cache expiration is set to 300 seconds using Redis standalone cache.
                """.formatted(i);

            artifactService.createArtifact(
                projectId,
                new CreateArtifactRequest(
                    "service-spec-" + i + ".md",
                    "DOCUMENTATION",
                    "LOCAL",
                    "spec-" + i,
                    content,
                    "Initial spec for component " + i
                ),
                ActorContext.human(userId, userEmail)
            );
        }
    }

    @Test
    @DisplayName("Performance Benchmark: Hybrid Retrieval, Context Packaging, ONNX Inference Latencies")
    void runPerformanceLatencyBenchmarks() {
        int warmupIterations = 5;
        int benchmarkIterations = 30;

        String query = "JWT RS256 authentication Kafka streaming retry configuration";

        // 1. Benchmark ONNX Embedding Inference
        List<Long> embeddingLatencies = new ArrayList<>();
        for (int i = 0; i < warmupIterations + benchmarkIterations; i++) {
            long start = System.nanoTime();
            float[] vector = embeddingProvider.embed(query);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (i >= warmupIterations) {
                embeddingLatencies.add(elapsedMs);
            }
            assertThat(vector).hasSize(384);
        }

        // 2. Benchmark Hybrid Retrieval Engine
        List<Long> retrievalLatencies = new ArrayList<>();
        for (int i = 0; i < warmupIterations + benchmarkIterations; i++) {
            long start = System.nanoTime();
            List<SearchResultItem> results = retrievalEngine.search(projectId, query, 10);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (i >= warmupIterations) {
                retrievalLatencies.add(elapsedMs);
            }
            assertThat(results).isNotEmpty();
        }

        // 3. Benchmark Context Orchestrator Packaging
        List<Long> packagingLatencies = new ArrayList<>();
        for (int i = 0; i < warmupIterations + benchmarkIterations; i++) {
            long start = System.nanoTime();
            ContextPackageResponse pkg = orchestratorService.buildContextPackage(
                projectId,
                "BENCHMARK_RUNNER",
                query,
                "gpt-4o",
                2000
            );
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (i >= warmupIterations) {
                packagingLatencies.add(elapsedMs);
            }
            assertThat(pkg.formattedPackage()).isNotBlank();
        }

        // Calculate percentiles
        LatencyStats embeddingStats = LatencyStats.calculate(embeddingLatencies);
        LatencyStats retrievalStats = LatencyStats.calculate(retrievalLatencies);
        LatencyStats packagingStats = LatencyStats.calculate(packagingLatencies);

        log.info("=== PERFORMANCE BENCHMARK RESULTS (N={}) ===", benchmarkIterations);
        log.info("ONNX Embedding Inference Latency: p50={}ms, p95={}ms, p99={}ms, avg={}ms",
            embeddingStats.p50, embeddingStats.p95, embeddingStats.p99, embeddingStats.avg);
        log.info("Hybrid Retrieval Latency:         p50={}ms, p95={}ms, p99={}ms, avg={}ms",
            retrievalStats.p50, retrievalStats.p95, retrievalStats.p99, retrievalStats.avg);
        log.info("Context Packaging Latency:        p50={}ms, p95={}ms, p99={}ms, avg={}ms",
            packagingStats.p50, packagingStats.p95, packagingStats.p99, packagingStats.avg);

        // Assert reasonable production bounds
        assertThat(embeddingStats.p50).isLessThan(200);
        assertThat(retrievalStats.p50).isLessThan(300);
        assertThat(packagingStats.p50).isLessThan(400);
    }

    @Test
    @DisplayName("Token Budget Truncation: Pack-to-budget behavior strictly adheres to token ceiling")
    void testTokenBudgetPackingAndTruncation() {
        int smallBudget = 300;
        ContextPackageResponse smallPkg = orchestratorService.buildContextPackage(
            projectId,
            "AGENT_LOW_BUDGET",
            "Authentication Kafka microservice configuration",
            "gpt-4o",
            smallBudget
        );

        int largeBudget = 4000;
        ContextPackageResponse largePkg = orchestratorService.buildContextPackage(
            projectId,
            "AGENT_HIGH_BUDGET",
            "Authentication Kafka microservice configuration",
            "gpt-4o",
            largeBudget
        );

        assertThat(smallPkg.estimatedTokens()).isLessThanOrEqualTo(smallBudget);
        assertThat(largePkg.estimatedTokens()).isGreaterThan(smallPkg.estimatedTokens());
        assertThat(largePkg.formattedPackage().length()).isGreaterThan(smallPkg.formattedPackage().length());
    }

    @Test
    @DisplayName("Database Query Plan Verification: EXPLAIN ANALYZE on critical paths")
    void testDatabaseQueryPlansExplainAnalyze() {
        // Query 1: Vector similarity search with pgvector
        float[] sampleVec = embeddingProvider.embed("test query");
        String vecStr = "[" + java.util.stream.IntStream.range(0, sampleVec.length)
            .mapToObj(i -> String.valueOf(sampleVec[i]))
            .collect(Collectors.joining(",")) + "]";

        List<String> explainVector = jdbcClient.sql("""
            EXPLAIN ANALYZE
            SELECT e.chunk_id, (1 - (e.embedding <=> ?::vector)) AS similarity
            FROM embeddings e
            WHERE e.project_id = ?
            ORDER BY e.embedding <=> ?::vector ASC
            LIMIT 10
            """)
            .params(vecStr, projectId, vecStr)
            .query(String.class)
            .list();

        log.info("EXPLAIN ANALYZE - Vector Similarity Search:\n{}", String.join("\n", explainVector));
        assertThat(explainVector).isNotEmpty();

        // Query 2: Timeline events order by created_at DESC
        List<String> explainTimeline = jdbcClient.sql("""
            EXPLAIN ANALYZE
            SELECT * FROM timeline_events
            WHERE project_id = :pId
            ORDER BY created_at DESC
            LIMIT 50
            """)
            .param("pId", projectId)
            .query(String.class)
            .list();

        log.info("EXPLAIN ANALYZE - Timeline Events Query:\n{}", String.join("\n", explainTimeline));
        assertThat(explainTimeline).isNotEmpty();

        // Query 3: Tasks by project and status
        List<String> explainTasks = jdbcClient.sql("""
            EXPLAIN ANALYZE
            SELECT * FROM tasks
            WHERE project_id = :pId AND status = 'READY'
            ORDER BY created_at DESC
            """)
            .param("pId", projectId)
            .query(String.class)
            .list();

        log.info("EXPLAIN ANALYZE - Tasks By Project & Status:\n{}", String.join("\n", explainTasks));
        assertThat(explainTasks).isNotEmpty();

        // Memory profiling check
        Runtime runtime = Runtime.getRuntime();
        long totalMemoryMb = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMb = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMb = totalMemoryMb - freeMemoryMb;
        log.info("JVM Memory Profile: Used={}MB, Free={}MB, Total={}MB, Max={}MB",
            usedMemoryMb, freeMemoryMb, totalMemoryMb, runtime.maxMemory() / (1024 * 1024));
        assertThat(usedMemoryMb).isGreaterThan(0);
    }

    private record LatencyStats(double avg, long p50, long p95, long p99) {
        static LatencyStats calculate(List<Long> values) {
            if (values.isEmpty()) return new LatencyStats(0, 0, 0, 0);
            List<Long> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long p50 = sorted.get((int) (sorted.size() * 0.50));
            long p95 = sorted.get(Math.min((int) (sorted.size() * 0.95), sorted.size() - 1));
            long p99 = sorted.get(Math.min((int) (sorted.size() * 0.99), sorted.size() - 1));
            return new LatencyStats(avg, p50, p95, p99);
        }
    }
}
