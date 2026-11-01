package com.context_platform.aether.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    @DisplayName("Normalizes DB_URL, DB_USERNAME, DB_PASSWORD environment variables for Neon")
    void testNormalizeNeonDbEnvironmentVariables() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testSource", Map.of(
                "DB_URL", "jdbc:postgresql://ep-silent-moon-123456.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "aether_neon_user",
                "DB_PASSWORD", "neon_secure_password_987"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("jdbc:postgresql://ep-silent-moon-123456.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                env.getProperty("spring.datasource.url"));
        assertEquals("aether_neon_user", env.getProperty("spring.datasource.username"));
        assertEquals("neon_secure_password_987", env.getProperty("spring.datasource.password"));
    }

    @Test
    @DisplayName("Automatically appends sslmode=require if connecting to a Neon host without TLS parameter")
    void testEnforceTlsForNeonEndpoints() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testSource", Map.of(
                "DB_URL", "postgresql://neon_admin:pass123@ep-cool-pooler.us-east-2.aws.neon.tech/neondb"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("jdbc:postgresql://ep-cool-pooler.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                env.getProperty("spring.datasource.url"));
        assertEquals("neon_admin", env.getProperty("spring.datasource.username"));
        assertEquals("pass123", env.getProperty("spring.datasource.password"));
    }

    @Test
    @DisplayName("Supports Neon pooled connection endpoint with separate DB_USERNAME and DB_PASSWORD")
    void testNeonPooledConnectionWithExplicitCredentials() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testSource", Map.of(
                "DB_URL", "jdbc:postgresql://ep-fragrant-pond-a5b6c7-pooler.us-east-2.aws.neon.tech:5432/neondb",
                "DB_USERNAME", "pooler_user",
                "DB_PASSWORD", "pooler_secret"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        // sslmode=require should be injected
        assertEquals("jdbc:postgresql://ep-fragrant-pond-a5b6c7-pooler.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                env.getProperty("spring.datasource.url"));
        assertEquals("pooler_user", env.getProperty("spring.datasource.username"));
        assertEquals("pooler_secret", env.getProperty("spring.datasource.password"));
    }

    @Test
    @DisplayName("Normalizes Render/Heroku postgresql:// URL with user and password")
    void testNormalizeRenderPostgresqlUrl() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testSource", Map.of(
                "DATABASE_URL", "postgresql://aether_user:secret_pass@dpg-c123456789-a.oregon-postgres.render.com:5432/aether_prod_db"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("jdbc:postgresql://dpg-c123456789-a.oregon-postgres.render.com:5432/aether_prod_db",
                env.getProperty("spring.datasource.url"));
        assertEquals("aether_user", env.getProperty("spring.datasource.username"));
        assertEquals("secret_pass", env.getProperty("spring.datasource.password"));
    }

    @Test
    @DisplayName("Normalizes postgres:// scheme with query parameters like sslmode")
    void testNormalizePostgresSchemeWithParams() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testSource", Map.of(
                "SPRING_DATASOURCE_URL", "postgres://db_admin:strong_pass@postgres-host:5432/production?sslmode=require"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertEquals("jdbc:postgresql://postgres-host:5432/production?sslmode=require",
                env.getProperty("spring.datasource.url"));
        assertEquals("db_admin", env.getProperty("spring.datasource.username"));
        assertEquals("strong_pass", env.getProperty("spring.datasource.password"));
    }

    @Test
    @DisplayName("Does not modify existing standard jdbc:postgresql:// URL for local Docker")
    void testPassThroughStandardJdbcUrl() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testSource", Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/aether"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        // Should not have modified localhost URL
        assertEquals("jdbc:postgresql://localhost:5432/aether", env.getProperty("spring.datasource.url"));
    }
}
