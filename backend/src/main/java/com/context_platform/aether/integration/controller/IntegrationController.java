package com.context_platform.aether.integration.controller;

import com.context_platform.aether.security.UserPrincipal;
import com.context_platform.aether.integration.model.*;
import com.context_platform.aether.integration.service.IntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/projects/{projectId}/integrations", "/api/projects/{projectId}/integrations"})
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
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(integrationService.listIntegrations(projectId, principal != null ? principal.id() : null));
    }

    @PostMapping("/connect")
    public ResponseEntity<IntegrationHealth> connect(
        @PathVariable UUID projectId,
        @RequestBody ConnectIntegrationRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(integrationService.connectIntegration(projectId, request, principal != null ? principal.id() : null));
    }

    @PostMapping("/{provider}/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        integrationService.disconnectIntegration(projectId, provider, principal != null ? principal.id() : null);
        return ResponseEntity.ok(Map.of("message", "Integration disconnected successfully"));
    }

    @PostMapping("/{provider}/pause")
    public ResponseEntity<Map<String, String>> pause(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        integrationService.pauseIntegration(projectId, provider, principal != null ? principal.id() : null);
        return ResponseEntity.ok(Map.of("message", "Integration paused"));
    }

    @PostMapping("/{provider}/resume")
    public ResponseEntity<Map<String, String>> resume(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        integrationService.resumeIntegration(projectId, provider, principal != null ? principal.id() : null);
        return ResponseEntity.ok(Map.of("message", "Integration resumed"));
    }

    @PostMapping("/{provider}/sync")
    public ResponseEntity<IntegrationHealth> sync(
        @PathVariable UUID projectId,
        @PathVariable ProviderType provider,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(integrationService.triggerSync(projectId, provider, principal != null ? principal.id() : null));
    }

    @GetMapping("/dead-letter")
    public ResponseEntity<List<DeadLetterEventRecord>> listDeadLetter(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(integrationService.listDeadLetterEvents(projectId, principal != null ? principal.id() : null));
    }

    @PostMapping("/dead-letter/{dlqId}/retry")
    public ResponseEntity<Map<String, String>> retryDeadLetter(
        @PathVariable UUID projectId,
        @PathVariable UUID dlqId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        integrationService.retryDeadLetterEvent(projectId, dlqId, principal != null ? principal.id() : null);
        return ResponseEntity.ok(Map.of("message", "Dead letter event retried successfully"));
    }

    @DeleteMapping("/dead-letter/{dlqId}")
    public ResponseEntity<Map<String, String>> discardDeadLetter(
        @PathVariable UUID projectId,
        @PathVariable UUID dlqId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        integrationService.discardDeadLetterEvent(projectId, dlqId, principal != null ? principal.id() : null);
        return ResponseEntity.ok(Map.of("message", "Dead letter event discarded"));
    }

    @GetMapping("/deployments")
    public ResponseEntity<List<DeploymentRecord>> listDeployments(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "50") int limit,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(integrationService.listDeployments(projectId, limit, principal != null ? principal.id() : null));
    }

    @GetMapping("/deployments/latest")
    public ResponseEntity<DeploymentRecord> getLatestDeployment(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String environment,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.of(integrationService.getLatestDeployment(projectId, environment, principal != null ? principal.id() : null));
    }

    @GetMapping("/deployments/{deploymentId}")
    public ResponseEntity<DeploymentRecord> getDeployment(
        @PathVariable UUID projectId,
        @PathVariable UUID deploymentId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.of(integrationService.getDeployment(projectId, deploymentId, principal != null ? principal.id() : null));
    }

    @GetMapping("/deployments/{externalDeployId}/logs")
    public ResponseEntity<Map<String, String>> getDeploymentLogs(
        @PathVariable UUID projectId,
        @PathVariable String externalDeployId,
        @RequestParam(defaultValue = "100") int tailLines,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        String logs = integrationService.getDeploymentLogs(projectId, externalDeployId, tailLines, principal != null ? principal.id() : null);
        return ResponseEntity.ok(Map.of("deployId", externalDeployId, "logs", logs));
    }
}
