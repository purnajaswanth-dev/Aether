package com.context_platform.aether.integration.repository;

import com.context_platform.aether.integration.model.AgentDeviceRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AgentDeviceRepository {

    private final JdbcClient jdbcClient;

    public AgentDeviceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private final RowMapper<AgentDeviceRecord> rowMapper = (rs, rowNum) -> {
        Timestamp lastHbTs = rs.getTimestamp("last_heartbeat_at");
        return new AgentDeviceRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getString("device_code"),
            rs.getString("user_code"),
            rs.getString("device_token_hash"),
            rs.getString("hostname"),
            rs.getString("os"),
            rs.getString("local_path"),
            rs.getString("status"),
            rs.getTimestamp("expires_at").toInstant(),
            lastHbTs != null ? lastHbTs.toInstant() : null,
            rs.getTimestamp("created_at").toInstant()
        );
    };

    public AgentDeviceRecord createDevicePairing(UUID projectId, String deviceCode, String userCode, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
            INSERT INTO agent_devices (
                id, project_id, device_code, user_code, status, expires_at, created_at
            )
            VALUES (
                :id, :projectId, :deviceCode, :userCode, 'PENDING_PAIR', :expiresAt, now()
            )
            """)
            .param("id", id)
            .param("projectId", projectId)
            .param("deviceCode", deviceCode)
            .param("userCode", userCode)
            .param("expiresAt", Timestamp.from(expiresAt))
            .update();

        return findById(id).orElseThrow();
    }

    public Optional<AgentDeviceRecord> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM agent_devices WHERE id = :id")
            .param("id", id)
            .query(rowMapper)
            .optional();
    }

    public Optional<AgentDeviceRecord> findByDeviceCode(String deviceCode) {
        return jdbcClient.sql("SELECT * FROM agent_devices WHERE device_code = :deviceCode")
            .param("deviceCode", deviceCode)
            .query(rowMapper)
            .optional();
    }

    public Optional<AgentDeviceRecord> findByUserCode(String userCode) {
        return jdbcClient.sql("SELECT * FROM agent_devices WHERE user_code = :userCode")
            .param("userCode", userCode)
            .query(rowMapper)
            .optional();
    }

    public Optional<AgentDeviceRecord> findByTokenHash(String tokenHash) {
        return jdbcClient.sql("SELECT * FROM agent_devices WHERE device_token_hash = :hash")
            .param("hash", tokenHash)
            .query(rowMapper)
            .optional();
    }

    public List<AgentDeviceRecord> listByProject(UUID projectId) {
        return jdbcClient.sql("SELECT * FROM agent_devices WHERE project_id = :projectId ORDER BY created_at DESC")
            .param("projectId", projectId)
            .query(rowMapper)
            .list();
    }

    public void completePairing(UUID id, String tokenHash, String hostname, String os, String localPath) {
        jdbcClient.sql("""
            UPDATE agent_devices SET
                device_token_hash = :tokenHash,
                hostname = :hostname,
                os = :os,
                local_path = :localPath,
                status = 'ACTIVE',
                last_heartbeat_at = now()
            WHERE id = :id
            """)
            .param("id", id)
            .param("tokenHash", tokenHash)
            .param("hostname", hostname)
            .param("os", os)
            .param("localPath", localPath)
            .update();
    }

    public void updateHeartbeat(UUID id) {
        jdbcClient.sql("UPDATE agent_devices SET last_heartbeat_at = now(), status = 'ACTIVE' WHERE id = :id")
            .param("id", id)
            .update();
    }

    public void updateStatus(UUID id, String status) {
        jdbcClient.sql("UPDATE agent_devices SET status = :status WHERE id = :id")
            .param("id", id)
            .param("status", status)
            .update();
    }

    public void delete(UUID id) {
        jdbcClient.sql("DELETE FROM agent_devices WHERE id = :id")
            .param("id", id)
            .update();
    }
}

