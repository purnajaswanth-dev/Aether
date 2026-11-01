package com.context_platform.aether.mcp.controller;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.mcp.model.JsonRpcError;
import com.context_platform.aether.mcp.model.JsonRpcRequest;
import com.context_platform.aether.mcp.model.JsonRpcResponse;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.security.McpSecurityService;
import com.context_platform.aether.mcp.service.McpDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/mcp")
public class McpSseController {

    private static final Logger log = LoggerFactory.getLogger(McpSseController.class);

    private final McpSecurityService securityService;
    private final McpDispatcherService dispatcherService;

    private record SseSession(String sessionId, McpToken token, SseEmitter emitter) {}

    private final Map<String, SseSession> activeSessions = new ConcurrentHashMap<>();

    public McpSseController(McpSecurityService securityService, McpDispatcherService dispatcherService) {
        this.securityService = securityService;
        this.dispatcherService = dispatcherService;
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        McpToken token = securityService.authenticate(authHeader);

        String sessionId = UUID.randomUUID().toString();
        // 30 minute timeout for long-lived client connection
        SseEmitter emitter = new SseEmitter(1800_000L);

        SseSession session = new SseSession(sessionId, token, emitter);
        activeSessions.put(sessionId, session);

        emitter.onCompletion(() -> activeSessions.remove(sessionId));
        emitter.onTimeout(() -> activeSessions.remove(sessionId));
        emitter.onError(e -> activeSessions.remove(sessionId));

        try {
            // MCP legacy spec: send initial endpoint event with relative POST URL
            emitter.send(SseEmitter.event()
                .name("endpoint")
                .data("/api/mcp/message?sessionId=" + sessionId));
        } catch (IOException e) {
            emitter.completeWithError(e);
            activeSessions.remove(sessionId);
        }

        return emitter;
    }

    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcResponse> handleSseMessage(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestParam("sessionId") String sessionId,
        @RequestBody JsonRpcRequest request
    ) {
        McpToken token;
        try {
            token = securityService.authenticate(authHeader);
        } catch (ApiException e) {
            int code = e.getStatus().value() == 429 ? JsonRpcError.RATE_LIMITED : JsonRpcError.UNAUTHORIZED;
            return ResponseEntity.status(e.getStatus()).body(
                JsonRpcResponse.error(request != null ? request.id() : null, code, e.getMessage())
            );
        }

        SseSession session = activeSessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                JsonRpcResponse.error(request.id(), JsonRpcError.INVALID_REQUEST, "SSE session not found or expired: " + sessionId)
            );
        }

        // Verify session project binding
        if (!session.token().projectId().equals(token.projectId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                JsonRpcResponse.error(request.id(), JsonRpcError.FORBIDDEN, "Cross-project session access forbidden")
            );
        }

        JsonRpcResponse response = dispatcherService.dispatch(request, token);

        // Send response event to the SSE stream as well
        try {
            session.emitter().send(SseEmitter.event()
                .name("message")
                .data(response));
        } catch (Exception e) {
            log.warn("Failed to stream response over SSE for session {}: {}", sessionId, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}

