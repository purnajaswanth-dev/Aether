package com.context_platform.aether.workflow.controller;

import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.security.UserPrincipal;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.ActionRequestStatus;
import com.context_platform.aether.workflow.model.dto.ReviewActionRequest;
import com.context_platform.aether.workflow.model.dto.SubmitActionRequest;
import com.context_platform.aether.workflow.service.ActionRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/projects/{projectId}/actions", "/api/projects/{projectId}/actions"})
public class ActionRequestController {

    private final ActionRequestService actionRequestService;
    private final ProjectSecurityChecker securityChecker;

    public ActionRequestController(ActionRequestService actionRequestService, ProjectSecurityChecker securityChecker) {
        this.actionRequestService = actionRequestService;
        this.securityChecker = securityChecker;
    }

    @PostMapping
    public ResponseEntity<ActionRequestRecord> submitAction(
            @PathVariable UUID projectId,
            @RequestBody SubmitActionRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "MEMBER");
        }
        return ResponseEntity.ok(actionRequestService.submitActionRequest(projectId, null, req));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ActionRequestRecord>> listActions(
            @PathVariable UUID projectId,
            @RequestParam(required = false) ActionRequestStatus status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal != null) {
            securityChecker.verifyProjectAccess(projectId, principal.id(), "VIEWER");
        }
        return ResponseEntity.ok(actionRequestService.listAllActions(projectId, status));
    }

    @PostMapping("/{actionId}/review")
    public ResponseEntity<ActionRequestRecord> reviewAction(
            @PathVariable UUID projectId,
            @PathVariable UUID actionId,
            @RequestBody ReviewActionRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Authenticated human user required to review action requests");
        }
        securityChecker.verifyProjectAccess(projectId, principal.id(), "MEMBER");
        return ResponseEntity.ok(actionRequestService.reviewAction(projectId, actionId, principal.id(), req));
    }
}
