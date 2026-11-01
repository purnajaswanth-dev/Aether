package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionResponse;
import com.context_platform.aether.orchestrator.model.ContextPackageResponse;
import com.context_platform.aether.orchestrator.model.ContextRequestType;
import com.context_platform.aether.orchestrator.service.ContextOrchestratorService;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3: Context Orchestrator & Token Budgeting Tests")
public class TokenBudgetAndOrchestratorTest extends BaseTest {

    @Autowired
    private ContextOrchestratorService orchestratorService;

    @Test
    @DisplayName("Gate Test: Architecture review vs Debugging task returns clearly different, correct packages within budget")
    void testArchitectureReviewVsDebuggingPackages() {
        AuthResponse user = registerUser("orchestrator_lead@aether.test", "StrongPass123!", "Tech Lead");
        ProjectResponse project = createTestProject("Aether Gateway", user.user().id());
        ActorContext actor = ActorContext.human(user.user().id(), user.user().email());

        // Ingest Architecture specification
        String archDoc = """
            # System Architecture
            ## Monolith Topology
            Modular monolith with Spring Boot 3.3.5 and PostgreSQL 16.
            ## Constraints
            All cross-tenant queries must enforce row-level security.
            """;
        ArtifactResponse archArtifact = artifactService.createArtifact(
            project.id(),
            new CreateArtifactRequest("Core Architecture", "ARCHITECTURE", "AETHER", null, archDoc, "Baseline spec"),
            actor
        );

        // Ingest Deployment log
        String logDoc = """
            2026-10-31T12:00:00.000Z ERROR [auth-service] Connection timeout while connecting to PostgreSQL:5432
            org.postgresql.util.PSQLException: Connection refused
                at org.postgresql.core.v3.ConnectionFactoryImpl.openConnectionImpl
            """;
        ArtifactResponse logArtifact = artifactService.createArtifact(
            project.id(),
            new CreateArtifactRequest("Crash Logs", "LOG", "RENDER", null, logDoc, "Server log"),
            actor
        );

        // Approve an architectural decision
        DecisionResponse dec = decisionService.createDecision(
            project.id(),
            new CreateDecisionRequest("Adopt RLS Isolation", "SECURITY", "Need multi-tenant safety", "Enforce Postgres RLS", "Kernel-level protection", "All tables must have tenant policy", null),
            user.user().id()
        );
        decisionService.submitForReview(project.id(), dec.id(), new com.context_platform.aether.decision.DecisionActionRequest(0, "Submitting for review", null, null), user.user().id());
        decisionService.approveDecision(project.id(), dec.id(), new com.context_platform.aether.decision.DecisionActionRequest(1, "Approved", null, null), user.user().id());

        // --- Scenario 1: Architecture Review Package ---
        ContextPackageResponse archPackage = orchestratorService.buildContextPackage(
            project.id(),
            "CLAUDE",
            "Perform architectural review of system design, topology and security constraints",
            "claude-3-5-sonnet",
            10000
        );

        assertEquals(ContextRequestType.ARCHITECTURE_REVIEW, archPackage.requestType());
        assertTrue(archPackage.formattedPackage().contains("L1: Project Overview"));
        assertTrue(archPackage.formattedPackage().contains("Adopt RLS Isolation")); // Approved decision present
        assertTrue(archPackage.formattedPackage().contains("Monolith Topology"));
        // Deployment logs must NOT be included in Architecture Review
        assertFalse(archPackage.formattedPackage().contains("PSQLException"), "Architecture review must not include runtime error stack traces");

        // --- Scenario 2: Debugging Package ---
        ContextPackageResponse debugPackage = orchestratorService.buildContextPackage(
            project.id(),
            "ANTIGRAVITY",
            "Investigate and debug 500 error and connection timeout exception during auth",
            "default",
            10000
        );

        assertEquals(ContextRequestType.DEBUGGING, debugPackage.requestType());
        assertTrue(debugPackage.formattedPackage().contains("PSQLException") || debugPackage.formattedPackage().contains("Connection timeout"),
            "Debugging package must prioritize crash logs and connection timeout context");

        // Both packages must be within their token budget
        assertTrue(archPackage.estimatedTokens() <= archPackage.tokenBudget());
        assertTrue(debugPackage.estimatedTokens() <= debugPackage.tokenBudget());

        // Both packages wrap retrieved text in untrusted-data boundary delimiters
        assertTrue(archPackage.formattedPackage().contains("<<<UNTRUSTED_DATA_BOUNDARY: ARTIFACT"));
    }

    @Test
    @DisplayName("Gate Test: Token budget restriction yields highest-value items + staged retrieval index of omitted items")
    void testTokenBudgetOmittedIndex() {
        AuthResponse user = registerUser("budget_dev@aether.test", "StrongPass123!", "Senior Engineer");
        ProjectResponse project = createTestProject("Aether High Density", user.user().id());
        ActorContext actor = ActorContext.human(user.user().id(), user.user().email());

        // Ingest several artifacts
        for (int i = 1; i <= 5; i++) {
            artifactService.createArtifact(
                project.id(),
                new CreateArtifactRequest(
                    "Service Spec Part " + i,
                    "SPECIFICATION",
                    "AETHER",
                    null,
                    "# Service Component Part " + i + "\nDetailed specifications and configuration documentation for micro-component " + i + ".\nMust maintain SLA 99.99%.\n",
                    "Component " + i
                ),
                actor
            );
        }

        // Request package with a very tight budget (e.g. 150 tokens)
        ContextPackageResponse pkg = orchestratorService.buildContextPackage(
            project.id(),
            "CHATGPT",
            "Overview of service components",
            "default",
            150
        );

        assertTrue(pkg.estimatedTokens() <= 200, "Must respect tight token headroom");
        assertFalse(pkg.omittedItems().isEmpty(), "When budget is tight, lower-ranked items must be indexed under omittedItems");

        // Verify staged retrieval index is present in formatted output
        assertTrue(pkg.formattedPackage().contains("Staged Retrieval Index (Omitted Items)"));
        assertTrue(pkg.omittedItems().get(0).fetchCommand().contains("expand_chunk") || pkg.omittedItems().get(0).fetchCommand().contains("get_original_artifact"));
    }
}
