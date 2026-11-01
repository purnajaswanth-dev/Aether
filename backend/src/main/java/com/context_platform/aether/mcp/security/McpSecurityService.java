package com.context_platform.aether.mcp.security;

import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.repository.McpTokenRepository;
import com.context_platform.aether.security.RlsContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpSecurityService {

    private final McpTokenRepository tokenRepository;
    private final RlsContextHelper rlsContextHelper;
    private final SecureRandom secureRandom = new SecureRandom();

    // In-memory sliding-window rate limiter per token ID
    private final Map<UUID, Deque<Long>> rateLimitWindows = new ConcurrentHashMap<>();

    public McpSecurityService(McpTokenRepository tokenRepository, RlsContextHelper rlsContextHelper) {
        this.tokenRepository = tokenRepository;
        this.rlsContextHelper = rlsContextHelper;
    }

    public String generateRawToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String randomStr = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "aeth_mcp_" + randomStr;
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public String computePrefix(String rawToken) {
        if (rawToken.length() <= 15) {
            return rawToken;
        }
        return rawToken.substring(0, 15) + "...";
    }

    public McpToken authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header with Bearer token");
        }

        String rawToken = authHeader.substring(7).trim();
        String hash = hashToken(rawToken);

        McpToken token = tokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or unrecognized MCP Bearer token"));

        if (token.isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MCP token has been revoked by project administrator");
        }

        if (token.isPaused()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MCP connection is currently paused by project administrator");
        }

        if (!token.isValid()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MCP token has expired");
        }

        // Check sliding window rate limit
        checkRateLimit(token);

        // Update last used asynchronously or inline
        tokenRepository.updateLastUsed(token.id());

        return token;
    }

    public void checkRateLimit(McpToken token) {
        int maxPerMinute = token.rateLimitPerMinute() > 0 ? token.rateLimitPerMinute() : 60;
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        Deque<Long> timestamps = rateLimitWindows.computeIfAbsent(token.id(), k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxPerMinute) {
                throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded: maximum " + maxPerMinute + " requests per minute allowed for this MCP token"
                );
            }
            timestamps.addLast(now);
        }
    }

    public void enforcePermission(McpToken token, String requiredPermission) {
        if (!token.hasPermission(requiredPermission)) {
            throw new ProjectAccessDeniedException(
                "MCP token lacks required permission: " + requiredPermission + ". Contact project administrator to grant this permission."
            );
        }
    }

    public void setupSecurityContext(McpToken token) {
        // Enforce PostgreSQL Row-Level Security bound strictly to the token's project
        rlsContextHelper.setBypassRls(false);
        rlsContextHelper.setProjectContext(token.projectId());

        // Set ThreadLocal actor context to AGENT
        ActorContext agentActor = ActorContext.agent("MCP:" + token.clientPlatform().name() + ":" + token.tokenName());
        McpTokenContext.set(token, agentActor);
    }

    public void clearSecurityContext() {
        McpTokenContext.clear();
        rlsContextHelper.setBypassRls(true);
    }
}

