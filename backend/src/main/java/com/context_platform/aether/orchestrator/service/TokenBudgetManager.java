package com.context_platform.aether.orchestrator.service;

import com.context_platform.aether.orchestrator.model.ModelProfile;
import org.springframework.stereotype.Component;

@Component
public class TokenBudgetManager {

    public int calculateEffectiveBudget(ModelProfile profile, Integer requestedBudget) {
        int hardLimit = profile.contextWindowTokens() - profile.reservedResponseTokens() - profile.reservedSystemTokens();
        hardLimit = Math.min(hardLimit, profile.maxContextBudgetTokens());

        if (requestedBudget != null && requestedBudget > 0) {
            return Math.min(requestedBudget, hardLimit);
        }

        return hardLimit;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // Conservative estimation: ~3.8 characters per token for technical English / code
        return (int) Math.ceil(text.length() / 3.8);
    }
}

