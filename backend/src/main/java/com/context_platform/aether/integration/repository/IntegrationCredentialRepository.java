package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.CredentialEntity;
import com.context_platform.aether.integration.security.EncryptedData;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IntegrationCredentialRepository {

    private final JdbcClient jdbcClient;

    public IntegrationCredentialRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private final RowMapper<CredentialEntity> rowMapper = (rs, rowNum) -> new CredentialEntity(
        rs.getObject("id", UUID.class),
        rs.getObject("project_id", UUID.class),
        rs.getObject("integration_id", UUID.class),
        rs.getBytes("encrypted_secret"),
        rs.getBytes("initialization_vector"),
        rs.getString("key_version"),
        rs.getBytes("auth_tag"),
        rs.getString("masked_preview"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant()
    );

    public void save(UUID projectId, UUID integrationId, EncryptedData data) {
        // Upsert by integration_id: delete previous credential if exists
        jdbcClient.sql("DELETE FROM integration_credentials WHERE integration_id = :integrationId")
            .param("integrationId", integrationId)
            .update();

        jdbcClient.sql("""
            INSERT INTO integration_credentials (
                id, project_id, integration_id, encrypted_secret,
                initialization_vector, key_version, auth_tag, masked_preview, created_at, updated_at
            )
            VALUES (
                :id, :projectId, :integrationId, :encryptedSecret,
                :iv, :keyVersion, :authTag, :maskedPreview, now(), now()
            )
            """)
            .param("id", UUID.randomUUID())
            .param("projectId", projectId)
            .param("integrationId", integrationId)
            .param("encryptedSecret", data.ciphertext())
            .param("iv", data.iv())
            .param("keyVersion", data.keyVersion())
            .param("authTag", data.authTag())
            .param("maskedPreview", data.maskedPreview())
            .update();
    }

    public Optional<CredentialEntity> findByIntegrationId(UUID integrationId) {
        return jdbcClient.sql("SELECT * FROM integration_credentials WHERE integration_id = :integrationId")
            .param("integrationId", integrationId)
            .query(rowMapper)
            .optional();
    }

    public void deleteByIntegrationId(UUID integrationId) {
        jdbcClient.sql("DELETE FROM integration_credentials WHERE integration_id = :integrationId")
            .param("integrationId", integrationId)
            .update();
    }
}
