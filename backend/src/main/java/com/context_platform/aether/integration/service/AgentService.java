package com.context_platform.aether.integration.service;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.integration.model.AgentDeviceRecord;
import com.context_platform.aether.integration.model.AgentFileMetadataRecord;
import com.context_platform.aether.integration.repository.AgentDeviceRepository;
import com.context_platform.aether.integration.repository.AgentFileMetadataRepository;
import com.context_platform.aether.security.ProjectSecurityChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class AgentService {

    private final AgentDeviceRepository deviceRepository;
    private final AgentFileMetadataRepository fileMetadataRepository;
    private final ProjectSecurityChecker securityChecker;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgentService(
        AgentDeviceRepository deviceRepository,
        AgentFileMetadataRepository fileMetadataRepository,
        ProjectSecurityChecker securityChecker
    ) {
        this.deviceRepository = deviceRepository;
        this.fileMetadataRepository = fileMetadataRepository;
        this.securityChecker = securityChecker;
    }

    public record PairInitResponse(
        String deviceCode,
        String userCode,
        String verificationUri,
        int expiresIn
    ) {}

    public record PairPollResponse(
        String status,
        String deviceToken,
        UUID projectId,
        UUID deviceId
    ) {}

    public PairInitResponse initPairing(UUID projectId) {
        String deviceCode = "dc_" + UUID.randomUUID().toString().replace("-", "");
        String userCode = generateUserCode();
        Instant expiresAt = Instant.now().plusSeconds(900); // 15 minutes

        deviceRepository.createDevicePairing(projectId, deviceCode, userCode, expiresAt);

        return new PairInitResponse(
            deviceCode,
            userCode,
            "/integrations?agent_pair=" + userCode,
            900
        );
    }

    public PairPollResponse pollPairing(String deviceCode) {
        AgentDeviceRecord device = deviceRepository.findByDeviceCode(deviceCode)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Device code not recognized"));

        if (Instant.now().isAfter(device.expiresAt())) {
            throw new ApiException(HttpStatus.GONE, "Pairing session expired. Please run 'aether-agent pair' again.");
        }

        if ("ACTIVE".equalsIgnoreCase(device.status())) {
            return new PairPollResponse("ACTIVE", null, device.projectId(), device.id());
        }

        return new PairPollResponse("PENDING_PAIR", null, device.projectId(), device.id());
    }

    @Transactional
    public String confirmPairing(
        UUID projectId,
        String userCode,
        String hostname,
        String os,
        String localPath,
        UUID userId
    ) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");

        AgentDeviceRecord device = deviceRepository.findByUserCode(userCode.trim().toUpperCase())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invalid user pairing code"));

        if (!device.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Pairing code belongs to another project");
        }

        if (Instant.now().isAfter(device.expiresAt())) {
            throw new ApiException(HttpStatus.GONE, "Pairing code expired");
        }

        // Generate raw device token
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = "aeth_agent_" + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = hashToken(rawToken);

        deviceRepository.completePairing(
            device.id(),
            tokenHash,
            hostname != null ? hostname : "unknown-host",
            os != null ? os : "unknown-os",
            localPath != null ? localPath : ""
        );

        return rawToken;
    }

    public AgentDeviceRecord authenticateAgent(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing or invalid Agent Bearer token");
        }
        String rawToken = authHeader.substring(7).trim();
        String tokenHash = hashToken(rawToken);

        AgentDeviceRecord device = deviceRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid agent device token"));

        if (!"ACTIVE".equalsIgnoreCase(device.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Agent device is not active (status=" + device.status() + ")");
        }

        return device;
    }

    public void heartbeat(AgentDeviceRecord device) {
        deviceRepository.updateHeartbeat(device.id());
    }

    @Transactional
    public void syncFileMetadata(AgentDeviceRecord device, List<AgentFileMetadataRecord> files) {
        deviceRepository.updateHeartbeat(device.id());
        if (files != null) {
            for (AgentFileMetadataRecord f : files) {
                fileMetadataRepository.upsert(new AgentFileMetadataRecord(
                    null,
                    device.projectId(),
                    device.id(),
                    f.relativePath(),
                    f.contentHash(),
                    f.fileSizeBytes(),
                    f.mtime(),
                    f.language(),
                    f.symbols(),
                    Instant.now()
                ));
            }
        }
    }

    public List<AgentFileMetadataRecord> findRelevantFiles(UUID projectId, String query, int limit) {
        return fileMetadataRepository.findRelevant(projectId, query, limit);
    }

    public List<AgentDeviceRecord> listDevices(UUID projectId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");
        return deviceRepository.listByProject(projectId);
    }

    public void disconnectDevice(UUID projectId, UUID deviceId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "ADMIN");
        deviceRepository.updateStatus(deviceId, "DISCONNECTED");
    }

    private String generateUserCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append("-");
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

