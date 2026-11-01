package com.context_platform.aether.security;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionRepository;
import com.context_platform.aether.decision.DecisionResponse;
import com.context_platform.aether.decision.DecisionService;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.dto.ReviewActionRequest;
import com.context_platform.aether.workflow.model.dto.SubmitActionRequest;
import com.context_platform.aether.workflow.repository.ActionRequestRepository;
import com.context_platform.aether.workflow.service.ActionRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdversarialCrossProjectSecurityTest extends BaseTest {

    @Autowired
    private ArtifactService artifactService;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private DecisionService decisionService;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private ActionRequestService actionRequestService;

    @Autowired
    private ActionRequestRepository actionRequestRepository;

    private UUID projectAId;
    private UUID userAId;

    private UUID projectBId;
    private UUID userBId;

    private ArtifactResponse artifactB;
    private DecisionResponse decisionB;
    private ActionRequestRecord actionB;

    @BeforeEach
    void setUp() {
        // Project A (Tenant A)
        AuthResponse userA = registerUser("tenant_a_" + UUID.randomUUID() + "@example.com", "Password123!", "Tenant A");
        userAId = userA.user().id();
        ProjectResponse projectA = projectService.createProject(
                new CreateProjectRequest("Project Alpha", "Tenant A's isolation project", "WEB_APP", List.of("Java"), "/dirA", "LOCAL_FIRST"),
                userAId,
                userA.user().email()
        );
        projectAId = projectA.id();

        // Project B (Tenant B)
        AuthResponse userB = registerUser("tenant_b_" + UUID.randomUUID() + "@example.com", "Password123!", "Tenant B");
        userBId = userB.user().id();
        ProjectResponse projectB = projectService.createProject(
                new CreateProjectRequest("Project Beta", "Tenant B's confidential project", "API", List.of("Go"), "/dirB", "LOCAL_FIRST"),
                userBId,
                userB.user().email()
        );
        projectBId = projectB.id();

        // Seed confidential data in Project B
        ActorContext actorB = ActorContext.human(userBId, userB.user().email());
        artifactB = artifactService.createArtifact(
                projectBId,
                new CreateArtifactRequest("Beta Architecture Secret", "ARCHITECTURE", "MANUAL", "sec-001", "Confidential Secret Architecture", "Summary"),
                actorB
        );

        decisionB = decisionService.createDecision(
                projectBId,
                new CreateDecisionRequest("CONFIDENTIAL-01", "SECURITY", "Sensitive Architecture Decision", "Deploy in private enclave", "Protect secrets", "Isolation required", null),
                actorB
        );

        actionB = actionRequestService.submitActionRequest(
                projectBId,
                null,
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-beta-prod"), true)
        );
    }

    @Test
    @DisplayName("Adversarial: Tenant A cannot read Tenant B's artifacts through service API")
    void testTenantACannotReadTenantBArtifact() {
        assertThrows(Exception.class, () ->
                artifactService.getLatest(projectAId, artifactB.id(), userAId)
        );
    }

    @Test
    @DisplayName("Adversarial: Tenant A cannot approve or reject Tenant B's action request")
    void testTenantACannotReviewTenantBAction() {
        assertThrows(AccessDeniedException.class, () ->
                actionRequestService.reviewAction(
                        projectBId,
                        actionB.id(),
                        userAId, // User A has no membership in Project B
                        new ReviewActionRequest(true, "Malicious approval by Tenant A")
                )
        );
    }

    @Test
    @DisplayName("Adversarial: Artifact repository strictly isolates Project B artifacts from Project A")
    void testArtifactRepositoryIsolation() {
        // Project B can find artifactB
        assertTrue(artifactRepository.findArtifactById(artifactB.id(), projectBId).isPresent());

        // Project A querying artifactB returns empty
        assertTrue(artifactRepository.findArtifactById(artifactB.id(), projectAId).isEmpty());
    }

    @Test
    @DisplayName("Adversarial: Action request repository strictly isolates Project B actions from Project A")
    void testActionRequestRepositoryIsolation() {
        // Project B can find actionB
        assertTrue(actionRequestRepository.findById(projectBId, actionB.id()).isPresent());

        // Project A querying actionB returns empty
        assertTrue(actionRequestRepository.findById(projectAId, actionB.id()).isEmpty());

        // Attempting to claim execution scoped to Project A affects 0 rows
        int affected = actionRequestRepository.claimExecution(projectAId, actionB.id(), userAId, "Adversarial claim");
        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("Adversarial: Decision repository strictly isolates Project B decisions from Project A")
    void testDecisionRepositoryIsolation() {
        assertTrue(decisionRepository.findById(decisionB.id(), projectBId).isPresent());
        assertTrue(decisionRepository.findById(decisionB.id(), projectAId).isEmpty());
    }
}
