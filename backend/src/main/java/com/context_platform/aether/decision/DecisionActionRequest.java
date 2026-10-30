package com.context_platform.aether.decision;

import java.util.UUID;

public record DecisionActionRequest(
    Integer expectedLockVersion,
    String notes,
    UUID linkedDecisionId, // for supersede
    UUID evidenceArtifactVersionId
) {}
