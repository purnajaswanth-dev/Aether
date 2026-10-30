package com.context_platform.aether.decision;

import java.util.List;

public record DecisionDetailResponse(
    DecisionResponse decision,
    List<DecisionVersion> versions,
    List<DecisionEvidence> evidence
) {}

