package com.context_platform.aether.orchestrator.repository;

import com.context_platform.aether.orchestrator.model.ModelProfile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ModelProfileRepository {

    private final JdbcClient jdbcClient;

    public ModelProfileRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ModelProfile findByModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return getDefaultProfile();
        }

        String normalized = modelId.toLowerCase().trim();
        Optional<ModelProfile> profile = jdbcClient.sql("""
            SELECT model_id, context_window_tokens, reserved_response_tokens, reserved_system_tokens, max_context_budget_tokens
            FROM model_profiles
            WHERE model_id = ?
            """)
            .param(normalized)
            .query((rs, rowNum) -> new ModelProfile(
                rs.getString("model_id"),
                rs.getInt("context_window_tokens"),
                rs.getInt("reserved_response_tokens"),
                rs.getInt("reserved_system_tokens"),
                rs.getInt("max_context_budget_tokens")
            ))
            .optional();

        return profile.orElseGet(this::getDefaultProfile);
    }

    public ModelProfile getDefaultProfile() {
        return jdbcClient.sql("""
            SELECT model_id, context_window_tokens, reserved_response_tokens, reserved_system_tokens, max_context_budget_tokens
            FROM model_profiles
            WHERE model_id = 'default'
            """)
            .query((rs, rowNum) -> new ModelProfile(
                rs.getString("model_id"),
                rs.getInt("context_window_tokens"),
                rs.getInt("reserved_response_tokens"),
                rs.getInt("reserved_system_tokens"),
                rs.getInt("max_context_budget_tokens")
            ))
            .optional()
            .orElse(new ModelProfile("default", 32000, 4096, 2048, 16000));
    }
}

