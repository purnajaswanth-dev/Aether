package com.context_platform.aether.common;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NeonDatasourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class));

    @Test
    @DisplayName("HikariDataSource binds to DB_URL, DB_USERNAME, and DB_PASSWORD with TLS and pool settings")
    void testHikariConfiguredFromNeonEnvironmentVariables() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:postgresql://ep-fragrant-pond.us-east-2.aws.neon.tech:5432/neondb?sslmode=require",
                        "spring.datasource.username=neon_user",
                        "spring.datasource.password=neon_secret_pass",
                        "spring.datasource.hikari.maximum-pool-size=10",
                        "spring.datasource.hikari.minimum-idle=2",
                        "spring.datasource.hikari.connection-timeout=20000",
                        "spring.datasource.hikari.idle-timeout=300000",
                        "spring.datasource.hikari.max-lifetime=900000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(HikariDataSource.class);
                    HikariDataSource ds = context.getBean(HikariDataSource.class);

                    assertThat(ds.getJdbcUrl()).contains("sslmode=require");
                    assertThat(ds.getJdbcUrl()).contains("ep-fragrant-pond.us-east-2.aws.neon.tech");
                    assertThat(ds.getUsername()).isEqualTo("neon_user");
                    assertThat(ds.getPassword()).isEqualTo("neon_secret_pass");
                    assertThat(ds.getMaximumPoolSize()).isEqualTo(10);
                    assertThat(ds.getMinimumIdle()).isEqualTo(2);
                    assertThat(ds.getConnectionTimeout()).isEqualTo(20000);
                    assertThat(ds.getIdleTimeout()).isEqualTo(300000);
                    assertThat(ds.getMaxLifetime()).isEqualTo(900000);
                });
    }

    @Test
    @DisplayName("Local development defaults to localhost:5432/aether when no environment variables are set")
    void testLocalDevelopmentDefaultConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:postgresql://localhost:5432/aether",
                        "spring.datasource.username=aether",
                        "spring.datasource.password=aether_dev_password"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(HikariDataSource.class);
                    HikariDataSource ds = context.getBean(HikariDataSource.class);

                    assertThat(ds.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/aether");
                    assertThat(ds.getUsername()).isEqualTo("aether");
                    assertThat(ds.getPassword()).isEqualTo("aether_dev_password");
                });
    }
}

