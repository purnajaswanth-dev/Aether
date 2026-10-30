package com.context_platform.aether.decision;

import com.context_platform.aether.common.InvalidStateTransitionException;

import java.util.Map;
import java.util.Set;

public class DecisionStateMachine {

    private static final Map<DecisionState, Set<DecisionState>> ALLOWED_TRANSITIONS = Map.of(
        DecisionState.PROPOSED, Set.of(DecisionState.UNDER_REVIEW, DecisionState.REJECTED),
        DecisionState.UNDER_REVIEW, Set.of(DecisionState.APPROVED, DecisionState.REJECTED, DecisionState.PROPOSED),
        DecisionState.APPROVED, Set.of(DecisionState.SUPERSEDED, DecisionState.IMPLEMENTED),
        DecisionState.IMPLEMENTED, Set.of(DecisionState.VERIFIED, DecisionState.UNDER_REVIEW, DecisionState.SUPERSEDED),
        DecisionState.VERIFIED, Set.of(DecisionState.SUPERSEDED),
        DecisionState.REJECTED, Set.of(DecisionState.PROPOSED),
        DecisionState.SUPERSEDED, Set.of()
    );

    public static void validateTransition(DecisionState from, DecisionState to) {
        if (from == to) {
            return;
        }
        Set<DecisionState> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new InvalidStateTransitionException(
                "Invalid decision lifecycle transition: Cannot move from " + from + " to " + to
            );
        }
    }
}
