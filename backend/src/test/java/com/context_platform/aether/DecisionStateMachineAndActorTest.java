package com.context_platform.aether;

import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.auth.RegisterRequest;
import com.context_platform.aether.common.ActorType;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.InvalidStateTransitionException;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionActionRequest;
import com.context_platform.aether.decision.DecisionResponse;
import com.context_platform.aether.decision.DecisionState;
import com.context_platform.aether.decision.DecisionStateMachine;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DecisionStateMachineAndActorTest extends BaseTest {

    @Test
    @DisplayName("Gate 4: An agent-typed actor cannot approve a decision; only authenticated human user can approve")
    void testAgentCannotApproveDecision() {
        rlsContextHelper.setBypassRls(true);

        // 1. Setup user & project
        AuthResponse human = authService.register(
            new RegisterRequest("human_" + UUID.randomUUID() + "@team.com", "Password123!", "Human Engineer"),
            "127.0.0.1", "JUnit"
        );
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Decision Governance", "Testing rules", "Web", List.of("Java"), null, "LOCAL_FIRST"),
            human.user().id(),
            human.user().email()
        );

        // Set human context to propose decision
        setSecurityContext(human.user().id(), human.user().email(), ActorType.HUMAN_USER);
        DecisionResponse dec = decisionService.createDecision(
            project.id(),
            new CreateDecisionRequest("Adopt Vector Search", "ARCHITECTURE", "Need fast search", "Use pgvector HNSW", "Low latency", "Requires pgvector extension", null),
            human.user().id()
        );
        assertThat(dec.state()).isEqualTo("PROPOSED");

        // Submit for review
        decisionService.submitForReview(project.id(), dec.id(), new DecisionActionRequest(dec.lockVersion(), "Ready for review", null, null), human.user().id());

        // 2. Switch Security Context to an AGENT-typed actor
        setSecurityContext(human.user().id(), "agent-claude-3-5-sonnet", ActorType.AGENT);

        // Agent attempts to approve -> MUST FAIL with 403 Forbidden!
        assertThatThrownBy(() -> {
            decisionService.approveDecision(
                project.id(),
                dec.id(),
                new DecisionActionRequest(dec.lockVersion() + 1, "Agent auto-approving", null, null),
                human.user().id()
            );
        })
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("AI agents are strictly forbidden from approving decisions");

        // 3. Switch back to authenticated HUMAN_USER -> Approval succeeds!
        setSecurityContext(human.user().id(), human.user().email(), ActorType.HUMAN_USER);
        DecisionResponse approved = decisionService.approveDecision(
            project.id(),
            dec.id(),
            new DecisionActionRequest(dec.lockVersion() + 1, "Human engineer approved", null, null),
            human.user().id()
        );

        assertThat(approved.state()).isEqualTo("APPROVED");
        assertThat(approved.currentVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("Decision State Machine transitions validation")
    void testDecisionStateMachineTransitions() {
        // Valid transitions
        DecisionStateMachine.validateTransition(DecisionState.PROPOSED, DecisionState.UNDER_REVIEW);
        DecisionStateMachine.validateTransition(DecisionState.UNDER_REVIEW, DecisionState.APPROVED);
        DecisionStateMachine.validateTransition(DecisionState.APPROVED, DecisionState.IMPLEMENTED);
        DecisionStateMachine.validateTransition(DecisionState.IMPLEMENTED, DecisionState.VERIFIED);
        DecisionStateMachine.validateTransition(DecisionState.APPROVED, DecisionState.SUPERSEDED);

        // Invalid transitions must throw InvalidStateTransitionException
        assertThatThrownBy(() -> {
            DecisionStateMachine.validateTransition(DecisionState.PROPOSED, DecisionState.APPROVED); // cannot jump directly without review
        }).isInstanceOf(InvalidStateTransitionException.class);

        assertThatThrownBy(() -> {
            DecisionStateMachine.validateTransition(DecisionState.PROPOSED, DecisionState.VERIFIED);
        }).isInstanceOf(InvalidStateTransitionException.class);

        assertThatThrownBy(() -> {
            DecisionStateMachine.validateTransition(DecisionState.SUPERSEDED, DecisionState.APPROVED); // terminal state
        }).isInstanceOf(InvalidStateTransitionException.class);
    }

    private void setSecurityContext(UUID userId, String email, ActorType actorType) {
        UserPrincipal principal = new UserPrincipal(userId, email, email, actorType);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

