package com.context_platform.aether.integration.registry;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.integration.adapter.IntegrationAdapter;
import com.context_platform.aether.integration.model.CapabilityDescriptor;
import com.context_platform.aether.integration.model.ProviderType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class IntegrationRegistry {

    private final Map<ProviderType, IntegrationAdapter> adapters = new EnumMap<>(ProviderType.class);

    public IntegrationRegistry(List<IntegrationAdapter> adapterList) {
        for (IntegrationAdapter adapter : adapterList) {
            adapters.put(adapter.getProviderType(), adapter);
        }
    }

    public IntegrationAdapter getAdapter(ProviderType provider) {
        IntegrationAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No integration adapter registered for provider: " + provider);
        }
        return adapter;
    }

    public Optional<IntegrationAdapter> findAdapter(ProviderType provider) {
        return Optional.ofNullable(adapters.get(provider));
    }

    public List<CapabilityDescriptor> getAllCapabilities() {
        return adapters.values().stream()
            .map(IntegrationAdapter::checkCapabilities)
            .toList();
    }
}

