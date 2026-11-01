package com.context_platform.aether.integration.adapter;

import com.context_platform.aether.integration.model.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Common integration adapter interface defined in Constitution Rule 2:
 * "Every external provider is a separate adapter implementing one common interface:
 * connect, authenticate, checkCapabilities, retrieve, receiveEvents, normalizeEvents,
 * saveArtifact, syncStatus, handleError, disconnect. No provider-specific logic in shared services."
 */
public interface IntegrationAdapter {

    ProviderType getProviderType();

    CapabilityDescriptor checkCapabilities();

    IntegrationHealth connect(UUID projectId, Map<String, Object> config, String credential);

    boolean authenticate(UUID projectId, String credential);

    List<NormalizedEvent> retrieve(UUID projectId, String cursor, int limit);

    NormalizedEvent receiveEvents(UUID projectId, String payload, Map<String, String> headers);

    NormalizedEvent normalizeEvents(UUID projectId, String rawPayload, Map<String, String> headers);

    UUID saveArtifact(UUID projectId, NormalizedEvent event);

    IntegrationHealth syncStatus(UUID projectId);

    void handleError(UUID projectId, Exception error, String context);

    void disconnect(UUID projectId);
}

