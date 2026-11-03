package com.context_platform.aether.data.controller;

import com.context_platform.aether.data.model.DeleteProjectRequest;
import com.context_platform.aether.data.model.ProjectExportArchive;
import com.context_platform.aether.data.service.DataManagementService;
import com.context_platform.aether.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/projects/{projectId}", "/api/projects/{projectId}"})
@Tag(name = "Data Management", description = "Endpoints for project data archive export and cascading deletion")
public class DataManagementController {

    private final DataManagementService dataManagementService;

    public DataManagementController(DataManagementService dataManagementService) {
        this.dataManagementService = dataManagementService;
    }

    @GetMapping("/export")
    @Operation(summary = "Export complete project memory archive as structured JSON")
    public ResponseEntity<ProjectExportArchive> exportProject(
        @PathVariable UUID projectId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProjectExportArchive archive = dataManagementService.exportProjectData(projectId, principal.id(), principal.email());
        return ResponseEntity.ok(archive);
    }

    @DeleteMapping
    @Operation(summary = "Permanently delete project and cascade delete all associated records and storage")
    public ResponseEntity<Map<String, String>> deleteProject(
        @PathVariable UUID projectId,
        @Valid @RequestBody DeleteProjectRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        dataManagementService.deleteProjectData(projectId, principal.id(), request.confirmationCode());
        return ResponseEntity.ok(Map.of(
            "message", "Project and all associated records permanently purged successfully.",
            "projectId", projectId.toString()
        ));
    }
}

