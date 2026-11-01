package com.context_platform.aether.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(
    String jsonrpc,
    Object id,
    String method,
    Map<String, Object> params
) {
    public boolean isNotification() {
        return id == null;
    }
}

