package com.context_platform.aether.workflow.controller;

import com.context_platform.aether.auth.User;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.ActionRequestStatus;
import com.context_platform.aether.workflow.model.dto.ReviewActionRequest;
import com.context_platform.aether.workflow.model.dto.SubmitActionRequest;
import com.context_platform.aether.workflow.service.ActionRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/actions")
public class ActionRequestController {

    private final ActionRequestService actionRequestService;

    public ActionRequestController(ActionRequestService actionRequestService) {
        this.actionRequestService = actionRequestService;
    }

    @PostMapping
    public ResponseEntity<ActionRequestRecord> submitAction(
            @PathVariable UUID projectId,
            @RequestBody SubmitActionRequest req
    ) {
        return ResponseEntity.ok(actionRequestService.submitActionRequest(projectId, null, req));
    }

    @GetMapping
    public ResponseEntity<List<ActionRequestRecord>> listActions(
            @PathVariable UUID projectId,
            @RequestParam(required = false) ActionRequestStatus status
    ) {
        return ResponseEntity.ok(actionRequestService.listAllActions(projectId, status));
    }

    @PostMapping("/{actionId}/review")
    public ResponseEntity<ActionRequestRecord> reviewAction(
            @PathVariable UUID projectId,
            @PathVariable UUID actionId,
            @RequestBody ReviewActionRequest req,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new AccessDeniedException("Authenticated human user required to review action requests");
        }
        return ResponseEntity.ok(actionRequestService.reviewAction(projectId, actionId, user.id(), req));
    }
}
