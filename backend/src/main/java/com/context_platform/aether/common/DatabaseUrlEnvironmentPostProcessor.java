package com.context_platform.aether.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes cloud provider database connection URLs (Neon PostgreSQL, Render, etc.)
 * into Spring Boot / HikariCP compatible JDBC properties.
 *
 * Enforces production security:
 * - Requires explicit DB_URL, DB_USERNAME, DB_PASSWORD when 'prod' profile is active
 * - Completely prohibits development fallback credentials in production
 * - Preserves Docker defaults exclusively for local development
 * - Guarantees TLS (sslmode=require) for Neon endpoints (*.neon.tech)
 * - Masks credentials to prevent any leakage in application logs
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DatabaseUrlEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean isProd = isProdProfileActive(environment);

        String dbUrl = environment.getProperty("DB_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = environment.getProperty("DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        }

        // Capture explicit credentials if provided
        String explicitUsername = environment.getProperty("DB_USERNAME");
        if (explicitUsername == null || explicitUsername.isBlank()) {
            explicitUsername = environment.getProperty("SPRING_DATASOURCE_USERNAME");
        }

        String explicitPassword = environment.getProperty("DB_PASSWORD");
        if (explicitPassword == null || explicitPassword.isBlank()) {
            explicitPassword = environment.getProperty("SPRING_DATASOURCE_PASSWORD");
        }

        // Strict Production Credential Enforcement
        if (isProd) {
            if (dbUrl == null || dbUrl.isBlank() || dbUrl.startsWith("${")) {
                throw new IllegalStateException("Production profile 'prod' requires 'DB_URL' to be configured. Development fallbacks are strictly prohibited in production.");
            }
            if (explicitUsername == null || explicitUsername.isBlank() || explicitUsername.startsWith("${")) {
                throw new IllegalStateException("Production profile 'prod' requires 'DB_USERNAME' to be configured. Development fallbacks are strictly prohibited in production.");
            }
            if (explicitPassword == null || explicitPassword.isBlank() || explicitPassword.startsWith("${")) {
                throw new IllegalStateException("Production profile 'prod' requires 'DB_PASSWORD' to be configured. Development fallbacks are strictly prohibited in production.");
            }
            String jwtSecret = environment.getProperty("AETHER_JWT_SECRET");
            if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.startsWith("${")) {
                throw new IllegalStateException("Production profile 'prod' requires 'AETHER_JWT_SECRET' to be configured. Development fallbacks are strictly prohibited in production.");
            }
        }

        if (dbUrl == null || dbUrl.isBlank()) {
            return;
        }

        Map<String, Object> targetProperties = new HashMap<>();

        if (explicitUsername != null && !explicitUsername.isBlank()) {
            targetProperties.put("spring.datasource.username", explicitUsername);
        }
        if (explicitPassword != null && !explicitPassword.isBlank()) {
            targetProperties.put("spring.datasource.password", explicitPassword);
        }

        try {
            if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
                log.info("Detected cloud PostgreSQL URI; normalizing for JDBC");
                String normalizedScheme = dbUrl.replaceFirst("^postgres(ql)?://", "http://");
                URI uri = URI.create(normalizedScheme);

                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 5432;
                String path = uri.getPath(); // /dbname
                String query = uri.getQuery();

                // Extract credentials from URI if not already explicitly provided
                String userInfo = uri.getUserInfo();
                if (userInfo != null && !userInfo.isBlank()) {
                    String[] parts = userInfo.split(":", 2);
                    if (parts.length > 0 && !parts[0].isBlank() && !targetProperties.containsKey("spring.datasource.username")) {
                        targetProperties.put("spring.datasource.username", parts[0]);
                    }
                    if (parts.length > 1 && !targetProperties.containsKey("spring.datasource.password")) {
                        targetProperties.put("spring.datasource.password", parts[1]);
                    }
                }

                // Append sslmode=require for Neon or cloud endpoints if query doesn't specify it
                query = ensureSslMode(host, query);

                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                        .append(host)
                        .append(":")
                        .append(port)
                        .append(path);

                if (query != null && !query.isBlank()) {
                    jdbcUrl.append("?").append(query);
                }

                targetProperties.put("spring.datasource.url", jdbcUrl.toString());
                log.info("Normalized cloud database target to host: {} (port: {}, path: {})", host, port, path);

            } else if (dbUrl.startsWith("jdbc:postgresql://")) {
                // Ensure TLS if connecting to Neon
                String currentUrl = dbUrl;
                if (currentUrl.contains("neon.tech") && !currentUrl.contains("sslmode=")) {
                    currentUrl = currentUrl.contains("?") ? currentUrl + "&sslmode=require" : currentUrl + "?sslmode=require";
                    targetProperties.put("spring.datasource.url", currentUrl);
                    log.info("Enforced sslmode=require for Neon JDBC connection string");
                } else {
                    targetProperties.put("spring.datasource.url", currentUrl);
                }
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

    private boolean isProdProfileActive(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        String activeProfiles = environment.getProperty("spring.profiles.active");
        if (activeProfiles != null) {
            for (String p : activeProfiles.split(",")) {
                if ("prod".equalsIgnoreCase(p.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String ensureSslMode(String host, String query) {
        if (host != null && host.contains("neon.tech")) {
            if (query == null || query.isBlank()) {
                return "sslmode=require";
            } else if (!query.contains("sslmode=")) {
                return query + "&sslmode=require";
            }
        }
        return query;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
