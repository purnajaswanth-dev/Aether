package com.context_platform.aether.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TimelineService {

    private final TimelineRepository timelineRepository;

    public TimelineService(TimelineRepository timelineRepository) {
        this.timelineRepository = timelineRepository;
    }

    @Transactional
    public TimelineEvent append(
        UUID projectId,
        UUID taskId,
        String sourcePlatform,
        String eventType,
        UUID associatedArtifactId,
        UUID associatedDecisionId,
        Integer decisionVersion,
        String status,
        String actorId,
        String actorType,
        String summary,
        String metadataJson
    ) {
        return timelineRepository.append(
            projectId, taskId, sourcePlatform, eventType,
            associatedArtifactId, associatedDecisionId, decisionVersion,
            status, actorId, actorType, summary, metadataJson
        );
    }

    @Transactional(readOnly = true)
    public List<TimelineEvent> listTimeline(UUID projectId, String eventType, String sourcePlatform, int limit) {
        return timelineRepository.listByProject(projectId, eventType, sourcePlatform, limit);
    }
}

