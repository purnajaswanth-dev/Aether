package com.context_platform.aether.event;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(UUID projectId, UUID userId, String action, String actorType, String ipAddress, String userAgent, String detailsJson) {
        auditRepository.record(projectId, userId, action, actorType, ipAddress, userAgent, detailsJson);
    }
}

