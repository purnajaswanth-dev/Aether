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
    @DisplayName("Does not modify existing standard jdbc:postgresql:// URL")
    void testPassThroughStandardJdbcUrl() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("testSource", Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/aether"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        // Should not have added cloudDatabaseUrlPostProcessor property source
        assertFalse(env.getPropertySources().contains("cloudDatabaseUrlPostProcessor"));
    }
}
