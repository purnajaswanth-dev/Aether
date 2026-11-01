package com.context_platform.aether.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class AetherMetrics {

    private final MeterRegistry registry;

    private final Counter chunksIndexedCounter;
    private final Counter ingestionFailuresCounter;
    private final Counter mcpToolCallsCounter;
    private final Counter retrievalQueriesCounter;

    private final Timer retrievalTimer;
    private final Timer contextPackagingTimer;

    public AetherMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.chunksIndexedCounter = Counter.builder("aether.ingestion.chunks.total")
            .description("Total number of artifact chunks indexed")
            .tag("status", "SUCCESS")
            .register(registry);

        this.ingestionFailuresCounter = Counter.builder("aether.ingestion.chunks.total")
            .description("Total number of chunk ingestion failures")
            .tag("status", "FAILED")
            .register(registry);

        this.mcpToolCallsCounter = Counter.builder("aether.mcp.tools.invocations")
            .description("Total number of MCP tool invocations")
            .register(registry);

        this.retrievalQueriesCounter = Counter.builder("aether.retrieval.queries.total")
            .description("Total number of hybrid retrieval queries executed")
            .register(registry);

        this.retrievalTimer = Timer.builder("aether.retrieval.latency")
            .description("Hybrid retrieval engine execution duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);

        this.contextPackagingTimer = Timer.builder("aether.context.packaging.latency")
            .description("Context orchestrator package construction duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public void recordChunkIndexed() {
        chunksIndexedCounter.increment();
    }

    public void recordIngestionFailure() {
        ingestionFailuresCounter.increment();
    }

    public void recordMcpToolCall(String toolName, boolean success) {
        registry.counter("aether.mcp.tools.invocations", "tool", toolName, "status", success ? "SUCCESS" : "ERROR").increment();
    }

    public void recordRetrievalQuery(long durationMs) {
        retrievalQueriesCounter.increment();
        retrievalTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordContextPackaging(long durationMs) {
        contextPackagingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}

