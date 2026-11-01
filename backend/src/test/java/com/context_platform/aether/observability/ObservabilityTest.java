package com.context_platform.aether.observability;

import com.context_platform.aether.BaseTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ObservabilityTest extends BaseTest {

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    @Autowired
    private AetherMetrics aetherMetrics;

    @Autowired
    private AetherHealthIndicator healthIndicator;

    @Test
    @DisplayName("Correlation ID: Incoming requests receive or preserve X-Correlation-ID in response and MDC")
    void testCorrelationIdFilterPropagation() throws ServletException, IOException {
        // 1. Request without header -> generates new correlation ID
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/projects");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        MockFilterChain filterChain1 = new MockFilterChain();

        correlationIdFilter.doFilter(request1, response1, filterChain1);

        String generatedCid = response1.getHeader("X-Correlation-ID");
        assertThat(generatedCid).isNotBlank();
        // MDC must be cleared after filter completion
        assertThat(MDC.get("correlationId")).isNull();

        // 2. Request with explicit header -> preserves correlation ID
        String customCid = "cid-custom-trace-998877";
        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/projects");
        request2.addHeader("X-Correlation-ID", customCid);
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        MockFilterChain filterChain2 = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                // Inside filter chain execution, MDC must contain correlation ID
                assertThat(MDC.get("correlationId")).isEqualTo(customCid);
            }
        };

        correlationIdFilter.doFilter(request2, response2, filterChain2);
        assertThat(response2.getHeader("X-Correlation-ID")).isEqualTo(customCid);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("Actuator Health Indicator: Validates PostgreSQL, Storage, and ONNX Model")
    void testAetherHealthIndicator() {
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("database");
        assertThat(health.getDetails().get("database").toString()).contains("PostgreSQL 16");
        assertThat(health.getDetails()).containsKey("storage");
        assertThat(health.getDetails()).containsKey("embeddingModel");
        assertThat(health.getDetails().get("embeddingModel").toString()).contains("dimension=384");
    }

    @Test
    @DisplayName("Custom Metrics: Micrometer registry captures counters, latencies, and tool calls")
    void testCustomMetricsRegistration() {
        aetherMetrics.recordChunkIndexed();
        aetherMetrics.recordIngestionFailure();
        aetherMetrics.recordMcpToolCall("get_artifact", true);
        aetherMetrics.recordRetrievalQuery(25);
        aetherMetrics.recordContextPackaging(40);

        Counter chunksSuccess = aetherMetrics.getRegistry().find("aether.ingestion.chunks.total")
            .tag("status", "SUCCESS").counter();
        assertThat(chunksSuccess).isNotNull();
        assertThat(chunksSuccess.count()).isGreaterThanOrEqualTo(1.0);

        Counter mcpCalls = aetherMetrics.getRegistry().find("aether.mcp.tools.invocations")
            .tag("tool", "get_artifact").counter();
        assertThat(mcpCalls).isNotNull();
        assertThat(mcpCalls.count()).isGreaterThanOrEqualTo(1.0);

        Timer retrievalTimer = aetherMetrics.getRegistry().find("aether.retrieval.latency").timer();
        assertThat(retrievalTimer).isNotNull();
        assertThat(retrievalTimer.count()).isGreaterThanOrEqualTo(1);
    }
}

