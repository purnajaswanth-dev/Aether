package com.context_platform.aether.project;

import com.context_platform.aether.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Endpoints for managing engineering projects and workspace configurations")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a new project with initialized memory namespace and catalog integrations")
    public ResponseEntity<ProjectResponse> createProject(
        @Valid @RequestBody CreateProjectRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.createProject(request, principal.id(), principal.email()));
    }

    @GetMapping
    @Operation(summary = "List all active projects for the authenticated user")
    public ResponseEntity<List<ProjectResponse>> listProjects(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.listProjects(principal.id()));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project details by project UUID")
    public ResponseEntity<ProjectResponse> getProject(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.getProject(projectId, principal.id()));
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update project metadata")
    public ResponseEntity<ProjectResponse> updateProject(
        @PathVariable UUID projectId,
        @Valid @RequestBody UpdateProjectRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.updateProject(projectId, request, principal.id()));
    }

    @PostMapping("/{projectId}/archive")
    @Operation(summary = "Archive an existing project")
    public ResponseEntity<Void> archiveProject(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        projectService.archiveProject(projectId, principal.id());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{projectId}/integrations")
    @Operation(summary = "Get integrations catalogue with honest connection statuses and planned capabilities")
    public ResponseEntity<List<IntegrationStatusDto>> getIntegrations(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.getIntegrationsCatalogue(projectId, principal.id()));
    }

    @GetMapping("/{projectId}/permissions")
    @Operation(summary = "Get project permissions matrix across all providers")
    public ResponseEntity<List<Map<String, Object>>> getPermissions(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(projectService.getPermissions(projectId, principal.id()));
    }
}
