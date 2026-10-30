package com.context_platform.aether.event;

import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/timeline")
@Tag(name = "Timeline", description = "Endpoints for retrieving chronological, append-only project events and state changes")
public class TimelineController {

    private final TimelineService timelineService;
    private final ProjectSecurityChecker securityChecker;

    public TimelineController(TimelineService timelineService, ProjectSecurityChecker securityChecker) {
        this.timelineService = timelineService;
        this.securityChecker = securityChecker;
    }

    @GetMapping
    @Operation(summary = "Get chronological project timeline events with optional filters")
    public ResponseEntity<List<TimelineEvent>> getTimeline(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String platform,
        @RequestParam(defaultValue = "100") int limit,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        securityChecker.verifyProjectAccess(projectId, principal.id(), "VIEWER");
        return ResponseEntity.ok(timelineService.listTimeline(projectId, eventType, platform, limit));
    }
}
