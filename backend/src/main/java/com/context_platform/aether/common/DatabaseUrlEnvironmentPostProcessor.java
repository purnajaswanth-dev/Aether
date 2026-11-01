package com.context_platform.aether.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes cloud provider database connection URLs (Render, Heroku, Supabase, Neon, etc.)
 * into Spring Boot / HikariCP compatible JDBC properties.
 *
 * Supports:
 * - postgres://user:pass@host:port/database
 * - postgresql://user:pass@host:port/database
 * - Standard jdbc:postgresql:// URLs (passed through unchanged)
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUrlEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbUrl = environment.getProperty("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        }

        if (dbUrl == null || dbUrl.isBlank()) {
            return;
        }

        Map<String, Object> targetProperties = new HashMap<>();

        try {
            if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
                log.info("Detected cloud PostgreSQL connection string; normalizing for Spring Data / JDBC");
                // Convert scheme to http temporarily to leverage Java's URI parser for userinfo, host, port, path
                String normalizedScheme = dbUrl.replaceFirst("^postgres(ql)?://", "http://");
                URI uri = URI.create(normalizedScheme);

                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 5432;
                String path = uri.getPath(); // /dbname
                String query = uri.getQuery();

                String userInfo = uri.getUserInfo();
                if (userInfo != null && !userInfo.isBlank()) {
                    String[] parts = userInfo.split(":", 2);
                    if (parts.length > 0 && !parts[0].isBlank()) {
                        targetProperties.put("spring.datasource.username", parts[0]);
                    }
                    if (parts.length > 1) {
                        targetProperties.put("spring.datasource.password", parts[1]);
                    }
                }

                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                        .append(host)
                        .append(":")
                        .append(port)
                        .append(path);

                if (query != null && !query.isBlank()) {
                    jdbcUrl.append("?").append(query);
                }

                targetProperties.put("spring.datasource.url", jdbcUrl.toString());
                log.info("Successfully normalized database target to jdbc:postgresql://{}:{}{}", host, port, path);
            }
        } catch (Exception e) {
            log.warn("Failed to automatically normalize database URL: {}. Hikari will use raw properties.", e.getMessage());
        }

        if (!targetProperties.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("cloudDatabaseUrlPostProcessor", targetProperties)
            );
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
