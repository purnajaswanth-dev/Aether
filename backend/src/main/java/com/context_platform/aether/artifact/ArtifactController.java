package com.context_platform.aether.artifact;

import com.context_platform.aether.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/projects/{projectId}/artifacts", "/api/projects/{projectId}/artifacts"})
@Tag(name = "Artifacts", description = "Endpoints for managing immutable, append-only engineering artifacts and version history")
public class ArtifactController {

    private final ArtifactService artifactService;

    public ArtifactController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @PostMapping
    @Operation(summary = "Create a new engineering artifact (stores version 1 with SHA-256 verification)")
    public ResponseEntity<ArtifactResponse> createArtifact(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateArtifactRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(artifactService.createArtifact(projectId, request, principal.id()));
    }

    @PostMapping("/{artifactId}/versions")
    @Operation(summary = "Append a new immutable version to an existing artifact")
    public ResponseEntity<ArtifactVersionResponse> createVersion(
        @PathVariable UUID projectId,
        @PathVariable UUID artifactId,
        @Valid @RequestBody CreateVersionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(artifactService.createVersion(projectId, artifactId, request, principal.id()));
    }

    @GetMapping
    @Operation(summary = "List project artifacts with optional filters")
    public ResponseEntity<List<ArtifactResponse>> listArtifacts(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) String search,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(artifactService.listArtifacts(projectId, type, platform, search, principal.id()));
    }

    @GetMapping("/{artifactId}/latest")
    @Operation(summary = "Get latest version and content of an artifact with SHA-256 integrity verification")
    public ResponseEntity<ArtifactResponse> getLatest(
        @PathVariable UUID projectId,
        @PathVariable UUID artifactId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(artifactService.getLatest(projectId, artifactId, principal.id()));
    }

    @GetMapping("/{artifactId}/versions/{version}")
    @Operation(summary = "Get specific version and verified content of an artifact")
    public ResponseEntity<ArtifactVersionResponse> getVersion(
        @PathVariable UUID projectId,
        @PathVariable UUID artifactId,
        @PathVariable int version,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(artifactService.getVersion(projectId, artifactId, version, principal.id()));
    }

    @GetMapping("/{artifactId}/versions")
    @Operation(summary = "List all version metadata for an artifact")
    public ResponseEntity<List<ArtifactVersionResponse>> listVersions(
        @PathVariable UUID projectId,
        @PathVariable UUID artifactId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(artifactService.listVersions(projectId, artifactId, principal.id()));
    }

    @GetMapping("/{artifactId}/compare")
    @Operation(summary = "Compare two versions of an artifact and receive a structured line-by-line diff")
    public ResponseEntity<ArtifactDiffResponse> compareVersions(
        @PathVariable UUID projectId,
        @PathVariable UUID artifactId,
        @RequestParam int v1,
        @RequestParam int v2,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(artifactService.compareVersions(projectId, artifactId, v1, v2, principal.id()));
    }
}

