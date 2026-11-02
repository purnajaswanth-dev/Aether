package com.context_platform.aether.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionCredentialEnforcementTest {

    @Configuration
    @Import(ProdRequirementVerifier.class)
    static class MinimalTestConfig {}

    @Component
    static class ProdRequirementVerifier {
        public ProdRequirementVerifier(
                @Value("${spring.datasource.url}") String url,
                @Value("${spring.datasource.username}") String username,
                @Value("${spring.datasource.password}") String password,
                @Value("${spring.flyway.user}") String flywayUser,
                @Value("${spring.flyway.password}") String flywayPassword,
                @Value("${aether.security.jwt.secret}") String jwtSecret,
                @Value("${aether.security.master-key}") String masterKey
        ) {}
    }

    @Configuration
    @Import(LocalRequirementVerifier.class)
    static class LocalDevTestConfig {}

    @Component
    static class LocalRequirementVerifier {
        public LocalRequirementVerifier(
                @Value("${spring.datasource.url}") String url,
                @Value("${spring.datasource.username}") String username,
                @Value("${spring.datasource.password}") String password
        ) {}
    }

    private Map<String, Object> validProdProperties() {
        return Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-prod-instance.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "aether_app",
                "DB_PASSWORD", "aether_app_runtime_pass_123",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "MIGRATION_DB_PASSWORD", "neondb_owner_migration_pass_456",
                "AETHER_JWT_SECRET", "super_secret_jwt_key_256_bits_minimum_length_required_for_prod",
                "AETHER_MASTER_KEY", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
    }

    private SpringApplication createProdApp(Map<String, Object> properties) {
        SpringApplication app = new SpringApplication(MinimalTestConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("prod");
        app.setDefaultProperties(properties);
        return app;
    }

    @Test
    @DisplayName("Production profile fails startup immediately when DB_URL is missing")
    void testProdFailsWhenDbUrlMissing() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_USERNAME", "aether_app",
                "DB_PASSWORD", "aether_app_pass",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "MIGRATION_DB_PASSWORD", "neondb_owner_pass",
                "AETHER_JWT_SECRET", "jwt_secret_value",
                "AETHER_MASTER_KEY", "master_key_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'DB_URL' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when DB_USERNAME is missing")
    void testProdFailsWhenDbUsernameMissing() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_PASSWORD", "aether_app_pass",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "MIGRATION_DB_PASSWORD", "neondb_owner_pass",
                "AETHER_JWT_SECRET", "jwt_secret_value",
                "AETHER_MASTER_KEY", "master_key_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'DB_USERNAME' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when DB_PASSWORD is missing")
    void testProdFailsWhenDbPasswordMissing() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "aether_app",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "MIGRATION_DB_PASSWORD", "neondb_owner_pass",
                "AETHER_JWT_SECRET", "jwt_secret_value",
                "AETHER_MASTER_KEY", "master_key_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'DB_PASSWORD' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when MIGRATION_DB_USERNAME is missing")
    void testProdFailsWhenMigrationDbUsernameMissing() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "aether_app",
                "DB_PASSWORD", "aether_app_pass",
                "MIGRATION_DB_PASSWORD", "neondb_owner_pass",
                "AETHER_JWT_SECRET", "jwt_secret_value",
                "AETHER_MASTER_KEY", "master_key_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'MIGRATION_DB_USERNAME' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when MIGRATION_DB_PASSWORD is missing")
    void testProdFailsWhenMigrationDbPasswordMissing() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "aether_app",
                "DB_PASSWORD", "aether_app_pass",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "AETHER_JWT_SECRET", "jwt_secret_value",
                "AETHER_MASTER_KEY", "master_key_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'MIGRATION_DB_PASSWORD' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when AETHER_JWT_SECRET is missing")
    void testProdFailsWhenJwtSecretMissing() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "aether_app",
                "DB_PASSWORD", "aether_app_pass",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "MIGRATION_DB_PASSWORD", "neondb_owner_pass",
                "AETHER_MASTER_KEY", "master_key_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'AETHER_JWT_SECRET' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when AETHER_MASTER_KEY is missing")
    void testProdFailsWhenMasterKeyMissing() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "aether_app",
                "DB_PASSWORD", "aether_app_pass",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "MIGRATION_DB_PASSWORD", "neondb_owner_pass",
                "AETHER_JWT_SECRET", "jwt_secret_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'AETHER_MASTER_KEY' to be configured");
    }

    @Test
    @DisplayName("Production profile rejects role collision when DB_USERNAME equals MIGRATION_DB_USERNAME")
    void testProdRejectsRoleCollision() {
        SpringApplication app = createProdApp(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "neondb_owner",
                "DB_PASSWORD", "owner_pass",
                "MIGRATION_DB_USERNAME", "neondb_owner",
                "MIGRATION_DB_PASSWORD", "owner_pass",
                "AETHER_JWT_SECRET", "jwt_secret_value",
                "AETHER_MASTER_KEY", "master_key_value"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production security violation: Runtime 'DB_USERNAME' and migration 'MIGRATION_DB_USERNAME' cannot be the same role");
    }

    @Test
    @DisplayName("Production profile resolves separated runtime and migration identities")
    void testProdSucceedsWithSeparatedIdentities() {
        SpringApplication app = createProdApp(validProdProperties());

        try (ConfigurableApplicationContext ctx = app.run()) {
            ConfigurableEnvironment env = ctx.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://ep-prod-instance.us-east-2.aws.neon.tech:5432/neondb?sslmode=require");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("aether_app");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("aether_app_runtime_pass_123");
            assertThat(env.getProperty("spring.datasource.hikari.pool-name")).isEqualTo("AetherProdHikariPool");
            assertThat(env.getProperty("spring.flyway.user")).isEqualTo("neondb_owner");
            assertThat(env.getProperty("spring.flyway.password")).isEqualTo("neondb_owner_migration_pass_456");
            assertThat(env.getProperty("spring.flyway.url")).isEqualTo("jdbc:postgresql://ep-prod-instance.us-east-2.aws.neon.tech:5432/neondb?sslmode=require");
            assertThat(env.getProperty("aether.security.master-key")).isEqualTo("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        }
    }

    @Test
    @DisplayName("Local development defaults exclusively to Docker PostgreSQL when running without prod profile")
    void testLocalDevelopmentPreservesDockerDefaults() {
        SpringApplication app = new SpringApplication(LocalDevTestConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext ctx = app.run()) {
            ConfigurableEnvironment env = ctx.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/aether");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("aether");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("aether_dev_password");
        }
    }
}
