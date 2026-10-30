package com.context_platform.aether.mcp.controller;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.mcp.model.JsonRpcError;
import com.context_platform.aether.mcp.model.JsonRpcRequest;
import com.context_platform.aether.mcp.model.JsonRpcResponse;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.security.McpSecurityService;
import com.context_platform.aether.mcp.service.McpDispatcherService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
public class McpStreamableHttpController {

    private final McpSecurityService securityService;
    private final McpDispatcherService dispatcherService;

    public McpStreamableHttpController(McpSecurityService securityService, McpDispatcherService dispatcherService) {
        this.securityService = securityService;
        this.dispatcherService = dispatcherService;
    }

    @GetMapping
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "name", "aether-mcp-server",
            "version", "1.0.0",
            "transports", new String[]{"streamable-http", "sse"},
            "status", "UP"
        ));
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<JsonRpcResponse> handleStreamableHttp(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody JsonRpcRequest request
    ) {
        if (request == null || request.method() == null) {
            return ResponseEntity.badRequest().body(
                JsonRpcResponse.error(null, JsonRpcError.INVALID_REQUEST, "Malformed JSON-RPC request: method is required")
            );
        }

        McpToken token;
        try {
            token = securityService.authenticate(authHeader);
        } catch (ApiException e) {
            int code = e.getStatus().value() == 429 ? JsonRpcError.RATE_LIMITED : JsonRpcError.UNAUTHORIZED;
            return ResponseEntity.status(e.getStatus()).body(
                JsonRpcResponse.error(request.id(), code, e.getMessage())
            );
        }

        JsonRpcResponse response = dispatcherService.dispatch(request, token);
        return ResponseEntity.ok(response);
    }
}
