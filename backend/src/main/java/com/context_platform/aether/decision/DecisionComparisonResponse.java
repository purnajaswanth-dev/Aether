package com.context_platform.aether.decision;

import java.util.List;

public record DecisionComparisonResponse(
    DecisionResponse decision,
    DecisionVersion originalProposal,
    List<DecisionVersion> reviewFindings,
    List<DecisionVersion> modifiedProposals,
    DecisionVersion approvedVersion
) {}

