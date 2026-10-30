package com.context_platform.aether.common;

import java.util.UUID;

public record ActorContext(
    String actorId,
    ActorType actorType,
    UUID userId // nullable if pure unauthenticated agent or system
) {
    public static ActorContext human(UUID userId, String email) {
        return new ActorContext(email != null ? email : userId.toString(), ActorType.HUMAN_USER, userId);
    }

    public static ActorContext agent(String agentId) {
        return new ActorContext(agentId, ActorType.AGENT, null);
    }

    public static ActorContext system() {
        return new ActorContext("SYSTEM", ActorType.HUMAN_USER, null);
    }
}
