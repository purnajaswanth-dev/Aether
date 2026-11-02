package com.context_platform.aether.integration.controller;

import com.context_platform.aether.security.UserPrincipal;
import com.context_platform.aether.integration.model.AgentDeviceRecord;
import com.context_platform.aether.integration.model.AgentFileMetadataRecord;
import com.context_platform.aether.integration.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    // Agent device-code pair initialization
    @PostMapping({"/api/v1/agent/pair/init", "/api/agent/pair/init"})
    public ResponseEntity<AgentService.PairInitResponse> initPairing(@RequestParam UUID projectId) {
        return ResponseEntity.ok(agentService.initPairing(projectId));
    }

    // Agent device-code polling
    @PostMapping({"/api/v1/agent/pair/poll", "/api/agent/pair/poll"})
    public ResponseEntity<AgentService.PairPollResponse> pollPairing(@RequestBody Map<String, String> body) {
        String deviceCode = body.get("deviceCode");
        return ResponseEntity.ok(agentService.pollPairing(deviceCode));
    }

    // Project user confirms pairing from UI
    @PostMapping({"/api/v1/projects/{projectId}/agent/confirm", "/api/projects/{projectId}/agent/confirm"})
    public ResponseEntity<Map<String, String>> confirmPairing(
        @PathVariable UUID projectId,
        @RequestBody Map<String, String> body,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        String userCode = body.get("userCode");
        String hostname = body.get("hostname");
        String os = body.get("os");
        String localPath = body.get("localPath");
        UUID userId = principal != null ? principal.id() : null;

        String deviceToken = agentService.confirmPairing(projectId, userCode, hostname, os, localPath, userId);
        return ResponseEntity.ok(Map.of(
            "status", "ACTIVE",
            "deviceToken", deviceToken
        ));
    }

    // Agent periodic heartbeat
    @PostMapping({"/api/v1/agent/heartbeat", "/api/agent/heartbeat"})
    public ResponseEntity<Map<String, String>> heartbeat(@RequestHeader("Authorization") String authHeader) {
        AgentDeviceRecord device = agentService.authenticateAgent(authHeader);
        agentService.heartbeat(device);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // Agent file metadata synchronization
    @PostMapping({"/api/v1/agent/sync-files", "/api/agent/sync-files"})
    public ResponseEntity<Map<String, Object>> syncFiles(
        @RequestHeader("Authorization") String authHeader,
        @RequestBody List<AgentFileMetadataRecord> files
    ) {
        AgentDeviceRecord device = agentService.authenticateAgent(authHeader);
        agentService.syncFileMetadata(device, files);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "syncedCount", files != null ? files.size() : 0));
    }

    // List connected agent devices
    @GetMapping({"/api/v1/projects/{projectId}/agent/devices", "/api/projects/{projectId}/agent/devices"})
    public ResponseEntity<List<AgentDeviceRecord>> listDevices(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(agentService.listDevices(projectId, principal != null ? principal.id() : null));
    }

    // Disconnect agent device
    @DeleteMapping({"/api/v1/projects/{projectId}/agent/devices/{deviceId}", "/api/projects/{projectId}/agent/devices/{deviceId}"})
    public ResponseEntity<Map<String, String>> disconnectDevice(
        @PathVariable UUID projectId,
        @PathVariable UUID deviceId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        agentService.disconnectDevice(projectId, deviceId, principal != null ? principal.id() : null);
        return ResponseEntity.ok(Map.of("message", "Agent device disconnected"));
    }

    // Search indexed agent files
    @GetMapping({"/api/v1/projects/{projectId}/agent/files", "/api/projects/{projectId}/agent/files"})
    public ResponseEntity<List<AgentFileMetadataRecord>> findRelevantFiles(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(agentService.findRelevantFiles(projectId, query, limit));
    }
}
