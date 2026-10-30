package com.context_platform.aether.mcp.security;

import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.mcp.model.McpToken;

public class McpTokenContext {

    private static final ThreadLocal<McpToken> CURRENT_TOKEN = new ThreadLocal<>();
    private static final ThreadLocal<ActorContext> CURRENT_ACTOR = new ThreadLocal<>();

    public static void set(McpToken token, ActorContext actor) {
        CURRENT_TOKEN.set(token);
        CURRENT_ACTOR.set(actor);
    }

    public static McpToken getCurrentToken() {
        return CURRENT_TOKEN.get();
    }

    public static ActorContext getCurrentActor() {
        ActorContext actor = CURRENT_ACTOR.get();
        return actor != null ? actor : ActorContext.system();
    }

    public static void clear() {
        CURRENT_TOKEN.remove();
        CURRENT_ACTOR.remove();
    }
}
