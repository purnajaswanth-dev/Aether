package com.context_platform.aether.observability;

import com.context_platform.aether.embedding.provider.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class AetherHealthIndicator implements HealthIndicator {

    private final JdbcClient jdbcClient;
    private final EmbeddingProvider embeddingProvider;
    private final Path storagePath;

    public AetherHealthIndicator(
        JdbcClient jdbcClient,
        EmbeddingProvider embeddingProvider,
        @Value("${aether.storage.local-path:./data/artifacts}") String storagePathStr
    ) {
        this.jdbcClient = jdbcClient;
        this.embeddingProvider = embeddingProvider;
        this.storagePath = Paths.get(storagePathStr).toAbsolutePath().normalize();
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // 1. Database Check
        try {
            Integer result = jdbcClient.sql("SELECT 1").query(Integer.class).single();
            if (result != null && result == 1) {
                builder.withDetail("database", "UP (PostgreSQL 16 + pgvector connected)");
            } else {
                return Health.down().withDetail("database", "DOWN: Unexpected query result").build();
            }
        } catch (Exception e) {
            return Health.down().withDetail("database", "DOWN: " + e.getMessage()).build();
        }

        // 2. Storage Check
        try {
            if (Files.exists(storagePath) && Files.isWritable(storagePath)) {
                builder.withDetail("storage", "UP (Directory writable at " + storagePath + ")");
            } else {
                builder.withDetail("storage", "WARN (Directory does not exist or unwritable)");
            }
        } catch (Exception e) {
            builder.withDetail("storage", "WARN: " + e.getMessage());
        }

        // 3. ONNX Embedding Model Check
        try {
            float[] sample = embeddingProvider.embed("health check");
            if (sample != null && sample.length > 0) {
                builder.withDetail("embeddingModel", "UP (ONNX model dimension=" + sample.length + ")");
            } else {
                builder.withDetail("embeddingModel", "DOWN (Model returned empty vector)");
            }
        } catch (Exception e) {
            builder.withDetail("embeddingModel", "DOWN: " + e.getMessage());
        }

        return builder.build();
    }
}

