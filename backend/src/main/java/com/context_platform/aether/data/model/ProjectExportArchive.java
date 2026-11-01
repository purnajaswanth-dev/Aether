package com.context_platform.aether.data.model;

import com.context_platform.aether.artifact.Artifact;
import com.context_platform.aether.artifact.ArtifactVersion;
import com.context_platform.aether.decision.DecisionVersion;
import com.context_platform.aether.decision.TechnicalDecision;
import com.context_platform.aether.event.TimelineEvent;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.TaskRecord;

import java.time.Instant;
import java.util.List;

public record ProjectExportArchive(
    ProjectResponse project,
    List<ArtifactWithVersions> artifacts,
    List<DecisionWithVersions> decisions,
    List<TimelineEvent> timelineEvents,
    List<TaskRecord> tasks,
    List<ActionRequestRecord> actionRequests,
    Instant exportedAt,
    String exportedBy
) {
    public record ArtifactWithVersions(Artifact artifact, List<ArtifactVersion> versions) {}
    public record DecisionWithVersions(TechnicalDecision decision, List<DecisionVersion> versions) {}
}

