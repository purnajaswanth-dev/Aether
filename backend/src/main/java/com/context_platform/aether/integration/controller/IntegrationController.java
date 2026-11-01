package com.context_platform.aether.integration.controller;

import com.context_platform.aether.auth.User;
import com.context_platform.aether.integration.model.*;
import com.context_platform.aether.integration.service.IntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/integrations")
public class IntegrationController {

    private final IntegrationService integrationService;

    public IntegrationController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/capabilities")
    public ResponseEntity<List<CapabilityDescriptor>> getCapabilities() {
        return ResponseEntity.ok(integrationService.listCapabilities());
    }

    @GetMapping
    public ResponseEntity<List<IntegrationSummaryResponse>> listIntegrations(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(integrationService.listIntegrations(projectId, user.id()));
    }

    @PostMapping("/connect")
    public ResponseEntity<IntegrationHealth> connect(
        @PathVariable UUID projectId,
        @RequestBody ConnectIntegrationRequest request,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(integrationService.connectIntegration(projectId, request, user.id()));
    }

    @PostMapping("/{provider}/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal User user
    ) {
        integrationService.disconnectIntegration(projectId, provider, user.id());
        return ResponseEntity.ok(Map.of("message", "Integration disconnected successfully"));
    }

    @PostMapping("/{provider}/pause")
    public ResponseEntity<Map<String, String>> pause(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal User user
    ) {
        integrationService.pauseIntegration(projectId, provider, user.id());
        return ResponseEntity.ok(Map.of("message", "Integration paused"));
    }

    @PostMapping("/{provider}/resume")
    public ResponseEntity<Map<String, String>> resume(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal User user
    ) {
        integrationService.resumeIntegration(projectId, provider, user.id());
        return ResponseEntity.ok(Map.of("message", "Integration resumed"));
    }

    @PostMapping("/{provider}/sync")
    public ResponseEntity<IntegrationHealth> sync(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(integrationService.triggerSync(projectId, provider, user.id()));
    }

    @GetMapping("/dead-letter")
    public ResponseEntity<List<DeadLetterEventRecord>> listDeadLetter(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(integrationService.listDeadLetterEvents(projectId, user.id()));
    }

    @PostMapping("/dead-letter/{dlqId}/retry")
    public ResponseEntity<Map<String, String>> retryDeadLetter(
        @PathVariable UUID projectId,
        @PathVariable UUID dlqId,
        @AuthenticationPrincipal User user
    ) {
        integrationService.retryDeadLetterEvent(projectId, dlqId, user.id());
        return ResponseEntity.ok(Map.of("message", "Dead letter event retried successfully"));
    }

    @DeleteMapping("/dead-letter/{dlqId}")
    public ResponseEntity<Map<String, String>> discardDeadLetter(
        @PathVariable UUID projectId,
        @PathVariable UUID dlqId,
        @AuthenticationPrincipal User user
    ) {
        integrationService.discardDeadLetterEvent(projectId, dlqId, user.id());
        return ResponseEntity.ok(Map.of("message", "Dead letter event discarded"));
    }

    @GetMapping("/deployments")
    public ResponseEntity<List<DeploymentRecord>> listDeployments(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "50") int limit,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(integrationService.listDeployments(projectId, limit, user.id()));
    }

    @GetMapping("/deployments/latest")
    public ResponseEntity<DeploymentRecord> getLatestDeployment(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String environment,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.of(integrationService.getLatestDeployment(projectId, environment, user.id()));
    }

    @GetMapping("/deployments/{deploymentId}")
    public ResponseEntity<DeploymentRecord> getDeployment(
        @PathVariable UUID projectId,
        @PathVariable UUID deploymentId,
        @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.of(integrationService.getDeployment(projectId, deploymentId, user.id()));
    }

    @GetMapping("/deployments/{externalDeployId}/logs")
    public ResponseEntity<Map<String, String>> getDeploymentLogs(
        @PathVariable UUID projectId,
        @PathVariable String externalDeployId,
        @RequestParam(defaultValue = "100") int tailLines,
        @AuthenticationPrincipal User user
    ) {
        String logs = integrationService.getDeploymentLogs(projectId, externalDeployId, tailLines, user.id());
        return ResponseEntity.ok(Map.of("deployId", externalDeployId, "logs", logs));
    }
}
