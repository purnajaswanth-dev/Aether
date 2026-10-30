package com.context_platform.aether.mcp.repository;

import com.context_platform.aether.mcp.model.McpClientPlatform;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.security.RlsContextHelper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class McpTokenRepository {

    private final JdbcClient jdbcClient;
    private final RlsContextHelper rlsContextHelper;

    public McpTokenRepository(JdbcClient jdbcClient, RlsContextHelper rlsContextHelper) {
        this.jdbcClient = jdbcClient;
        this.rlsContextHelper = rlsContextHelper;
    }

    private final RowMapper<McpToken> rowMapper = (rs, rowNum) -> {
        Array permissionsArray = rs.getArray("permissions");
        List<String> permissions = permissionsArray != null
            ? Arrays.asList((String[]) permissionsArray.getArray())
            : List.of();

        Timestamp expiresTs = rs.getTimestamp("expires_at");
        Timestamp lastUsedTs = rs.getTimestamp("last_used_at");
        Timestamp createdTs = rs.getTimestamp("created_at");

        return new McpToken(
            rs.getObject("id", UUID.class),
            rs.getObject("project_id", UUID.class),
            McpClientPlatform.valueOf(rs.getString("client_platform")),
            rs.getString("token_name"),
            rs.getString("token_prefix"),
            rs.getString("token_hash"),
            permissions,
            rs.getBoolean("is_revoked"),
            rs.getBoolean("is_paused"),
            rs.getInt("rate_limit_per_minute"),
            expiresTs != null ? expiresTs.toInstant() : null,
            lastUsedTs != null ? lastUsedTs.toInstant() : null,
            createdTs != null ? createdTs.toInstant() : Instant.now()
        );
    };

    public McpToken save(McpToken token) {
        UUID id = token.id() != null ? token.id() : UUID.randomUUID();
        String[] permissionsArr = token.permissions().toArray(new String[0]);

        jdbcClient.sql("""
            INSERT INTO mcp_tokens (
                id, project_id, client_platform, token_name, token_prefix,
                token_hash, permissions, is_revoked, is_paused,
                rate_limit_per_minute, expires_at, created_at
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?::text[], ?, ?,
                ?, ?, now()
            )
        """)
        .param(id)
        .param(token.projectId())
        .param(token.clientPlatform().name())
        .param(token.tokenName())
        .param(token.tokenPrefix())
        .param(token.tokenHash())
        .param(permissionsArr)
        .param(token.isRevoked())
        .param(token.isPaused())
        .param(token.rateLimitPerMinute())
        .param(token.expiresAt() != null ? Timestamp.from(token.expiresAt()) : null)
        .update();

        return findById(id).orElseThrow();
    }

    public Optional<McpToken> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM mcp_tokens WHERE id = ?")
            .param(id)
            .query(rowMapper)
            .optional();
    }

    /**
     * Resolves token by hash across projects to determine the tenant context.
     * Bypasses RLS strictly for the hash lookup, then the caller immediately binds
     * RLS to the resolved project_id.
     */
    public Optional<McpToken> findByTokenHash(String tokenHash) {
        boolean previousBypass = rlsContextHelper.isBypassRls();
        try {
            rlsContextHelper.setBypassRls(true);
            return jdbcClient.sql("SELECT * FROM mcp_tokens WHERE token_hash = ?")
                .param(tokenHash)
                .query(rowMapper)
                .optional();
        } finally {
            rlsContextHelper.setBypassRls(previousBypass);
        }
    }

    public List<McpToken> findByProjectId(UUID projectId) {
        return jdbcClient.sql("SELECT * FROM mcp_tokens WHERE project_id = ? ORDER BY created_at DESC")
            .param(projectId)
            .query(rowMapper)
            .list();
    }

    public void revokeToken(UUID id, UUID projectId) {
        jdbcClient.sql("UPDATE mcp_tokens SET is_revoked = true WHERE id = ? AND project_id = ?")
            .param(id)
            .param(projectId)
            .update();
    }

    public void setPaused(UUID id, UUID projectId, boolean paused) {
        jdbcClient.sql("UPDATE mcp_tokens SET is_paused = ? WHERE id = ? AND project_id = ?")
            .param(paused)
            .param(id)
            .param(projectId)
            .update();
    }

    public void updateLastUsed(UUID id) {
        boolean previousBypass = rlsContextHelper.isBypassRls();
        try {
            rlsContextHelper.setBypassRls(true);
            jdbcClient.sql("UPDATE mcp_tokens SET last_used_at = now() WHERE id = ?")
                .param(id)
                .update();
        } finally {
            rlsContextHelper.setBypassRls(previousBypass);
        }
    }
}
