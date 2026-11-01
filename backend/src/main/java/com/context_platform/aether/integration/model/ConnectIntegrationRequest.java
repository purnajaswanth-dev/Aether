package com.context_platform.aether.integration.model;

import java.util.Map;

public record ConnectIntegrationRequest(
    ProviderType provider,
    Map<String, Object> config,
    String credential
) {}

