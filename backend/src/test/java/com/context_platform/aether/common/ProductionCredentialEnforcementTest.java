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
                @Value("${aether.security.jwt.secret}") String jwtSecret
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

    @Test
    @DisplayName("Production profile fails startup immediately when DB_URL is missing")
    void testProdFailsWhenDbUrlMissing() {
        SpringApplication app = new SpringApplication(MinimalTestConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.profiles.active", "prod",
                "DB_USERNAME", "prod_user",
                "DB_PASSWORD", "prod_password",
                "AETHER_JWT_SECRET", "super_secret_jwt_key_256_bits_minimum_length_required_for_prod"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'DB_URL' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when DB_USERNAME is missing")
    void testProdFailsWhenDbUsernameMissing() {
        SpringApplication app = new SpringApplication(MinimalTestConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_PASSWORD", "prod_password",
                "AETHER_JWT_SECRET", "super_secret_jwt_key_256_bits_minimum_length_required_for_prod"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'DB_USERNAME' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when DB_PASSWORD is missing")
    void testProdFailsWhenDbPasswordMissing() {
        SpringApplication app = new SpringApplication(MinimalTestConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "prod_user",
                "AETHER_JWT_SECRET", "super_secret_jwt_key_256_bits_minimum_length_required_for_prod"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'DB_PASSWORD' to be configured");
    }

    @Test
    @DisplayName("Production profile fails startup immediately when AETHER_JWT_SECRET is missing")
    void testProdFailsWhenJwtSecretMissing() {
        SpringApplication app = new SpringApplication(MinimalTestConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "prod_user",
                "DB_PASSWORD", "prod_pass"
        ));

        assertThatThrownBy(app::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production profile 'prod' requires 'AETHER_JWT_SECRET' to be configured");
    }

    @Test
    @DisplayName("Production profile resolves configured Neon properties when all credentials are provided")
    void testProdSucceedsWithAllCredentialsProvided() {
        SpringApplication app = new SpringApplication(MinimalTestConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
                "spring.profiles.active", "prod",
                "DB_URL", "jdbc:postgresql://ep-prod-instance.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                "DB_USERNAME", "prod_neon_admin",
                "DB_PASSWORD", "strong_production_neon_password_987",
                "AETHER_JWT_SECRET", "super_secret_jwt_key_256_bits_minimum_length_required_for_prod"
        ));

        try (ConfigurableApplicationContext ctx = app.run()) {
            ConfigurableEnvironment env = ctx.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://ep-prod-instance.us-east-2.aws.neon.tech:5432/neondb?sslmode=require");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("prod_neon_admin");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("strong_production_neon_password_987");
            assertThat(env.getProperty("spring.datasource.hikari.pool-name")).isEqualTo("AetherProdHikariPool");
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
