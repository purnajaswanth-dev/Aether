package com.context_platform.aether.decision;

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
@RequestMapping("/api/v1/projects/{projectId}/decisions")
@Tag(name = "Technical Decisions", description = "Endpoints for proposing, reviewing, approving and comparing architectural decisions")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @PostMapping
    @Operation(summary = "Propose a new technical decision (starts in PROPOSED lifecycle state)")
    public ResponseEntity<DecisionResponse> createDecision(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateDecisionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.createDecision(projectId, request, principal.id()));
    }

    @GetMapping
    @Operation(summary = "List technical decisions with optional state and category filters")
    public ResponseEntity<List<DecisionResponse>> listDecisions(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String category,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.listDecisions(projectId, state, category, principal.id()));
    }

    @GetMapping("/{decisionId}")
    @Operation(summary = "Get decision details including complete version history and attached evidence")
    public ResponseEntity<DecisionDetailResponse> getDecision(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.getDecision(projectId, decisionId, principal.id()));
    }

    @PostMapping("/{decisionId}/review")
    @Operation(summary = "Submit a proposed decision for review (PROPOSED -> UNDER_REVIEW)")
    public ResponseEntity<DecisionResponse> submitForReview(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @RequestBody(required = false) DecisionActionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.submitForReview(
            projectId, decisionId,
            request != null ? request : new DecisionActionRequest(null, null, null, null),
            principal.id()
        ));
    }

    @PostMapping("/{decisionId}/approve")
    @Operation(summary = "Approve a decision. STRICT REQUIREMENT: Only authenticated human users can approve decisions.")
    public ResponseEntity<DecisionResponse> approveDecision(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @RequestBody(required = false) DecisionActionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.approveDecision(
            projectId, decisionId,
            request != null ? request : new DecisionActionRequest(null, null, null, null),
            principal.id()
        ));
    }

    @PostMapping("/{decisionId}/reject")
    @Operation(summary = "Reject a proposed decision with recorded rationale. Requires human reviewer.")
    public ResponseEntity<DecisionResponse> rejectDecision(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @RequestBody(required = false) DecisionActionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.rejectDecision(
            projectId, decisionId,
            request != null ? request : new DecisionActionRequest(null, null, null, null),
            principal.id()
        ));
    }

    @PostMapping("/{decisionId}/supersede")
    @Operation(summary = "Mark a decision as superseded by a newer decision")
    public ResponseEntity<DecisionResponse> supersedeDecision(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @RequestBody(required = false) DecisionActionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.supersedeDecision(
            projectId, decisionId,
            request != null ? request : new DecisionActionRequest(null, null, null, null),
            principal.id()
        ));
    }

    @PostMapping("/{decisionId}/implement")
    @Operation(summary = "Mark an approved decision as implemented")
    public ResponseEntity<DecisionResponse> markImplemented(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @RequestBody(required = false) DecisionActionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.markImplemented(
            projectId, decisionId,
            request != null ? request : new DecisionActionRequest(null, null, null, null),
            principal.id()
        ));
    }

    @PostMapping("/{decisionId}/verify")
    @Operation(summary = "Mark an implemented decision as verified with linked evidence")
    public ResponseEntity<DecisionResponse> markVerified(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @RequestBody(required = false) DecisionActionRequest request,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.markVerified(
            projectId, decisionId,
            request != null ? request : new DecisionActionRequest(null, null, null, null),
            principal.id()
        ));
    }

    @GetMapping("/{decisionId}/compare")
    @Operation(summary = "Compare original proposal, review findings, modified proposal, and final approved version side by side")
    public ResponseEntity<DecisionComparisonResponse> compareDecision(
        @PathVariable UUID projectId,
        @PathVariable UUID decisionId,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(decisionService.compareDecision(projectId, decisionId, principal.id()));
    }
}

