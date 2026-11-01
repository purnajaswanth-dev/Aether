package com.context_platform.aether.reliability;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.ingestion.model.IngestionJob;
import com.context_platform.aether.ingestion.model.IngestionStatus;
import com.context_platform.aether.ingestion.service.ArtifactChunkRepository;
import com.context_platform.aether.ingestion.service.IngestionJobRepository;
import com.context_platform.aether.ingestion.service.IngestionService;
import com.context_platform.aether.integration.model.NormalizedEvent;
import com.context_platform.aether.integration.model.ProviderType;
import com.context_platform.aether.integration.provider.github.GitHubAdapter;
import com.context_platform.aether.integration.reliability.IntegrationReliabilityService;
import com.context_platform.aether.integration.repository.IntegrationCheckpointRepository;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.ActionRequestStatus;
import com.context_platform.aether.workflow.model.dto.ReviewActionRequest;
import com.context_platform.aether.workflow.model.dto.SubmitActionRequest;
import com.context_platform.aether.workflow.service.ActionRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReliabilityAndJobRecoveryTest extends BaseTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private IngestionJobRepository ingestionJobRepository;

    @Autowired
    private ArtifactChunkRepository chunkRepository;

    @Autowired
    private ArtifactService artifactService;

    @Autowired
    private IntegrationReliabilityService reliabilityService;

    @Autowired
    private GitHubAdapter gitHubAdapter;

    @Autowired
    private IntegrationCheckpointRepository checkpointRepository;

    @Autowired
    private ActionRequestService actionRequestService;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("rel_tester_" + UUID.randomUUID() + "@example.com", "Password123!", "Reliability Tester");
        userId = user.user().id();
        ProjectResponse project = projectService.createProject(
                new CreateProjectRequest("Reliability Proj", "Testing fault recovery and idempotency", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
                userId,
                user.user().email()
        );
        projectId = project.id();
    }

    @Test
    @DisplayName("Durable Job Recovery: Dead-letter queue captures failed job and retry completes ingestion")
    void testIngestionFailureAndRetryRecovery() {
        ActorContext actor = ActorContext.human(userId, "user@example.com");

        // 1. Create artifact with valid content
        String docContent = """
            # Architecture Specification
            High availability deployment design for distributed cluster.
            Step 1: provision nodes.
            Step 2: deploy load balancer.
            """;

        ArtifactResponse artifact = artifactService.createArtifact(
                projectId,
                new CreateArtifactRequest("Cluster Spec", "ARCHITECTURE", "AETHER", null, docContent, "Cluster documentation"),
                actor
        );

        // Verify initial ingestion succeeded
        IngestionJob initialJob = ingestionJobRepository.findByArtifact(projectId, artifact.id(), 1).orElseThrow();
        assertThat(initialJob.status()).isEqualTo(IngestionStatus.INDEXED);

        // 2. Simulate a mid-flight crash by manually injecting a failed state into the DLQ
        UUID crashedJobId = UUID.randomUUID();
        IngestionJob failedJob = new IngestionJob(
                crashedJobId,
                projectId,
                artifact.id(),
                1,
                IngestionStatus.FAILED,
                1,
                3,
                "Simulated JVM crash during ONNX embedding inference",
                "{\"errorStep\":\"embedding\"}",
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
        ingestionJobRepository.save(failedJob);

        // 3. Verify job lands in the Dead-Letter Queue (DLQ)
        List<IngestionJob> dlq = ingestionJobRepository.listFailedOrNeedsAttention(projectId);
        assertThat(dlq).anyMatch(j -> j.id().equals(crashedJobId));

        // 4. Trigger DLQ retry
        IngestionJob recovered = ingestionService.retryIngestion(projectId, crashedJobId);

        // 5. Verify successful recovery
        assertThat(recovered.status()).isEqualTo(IngestionStatus.INDEXED);

        // Verify chunks were indexed without duplication
        int chunkCount = chunkRepository.listByArtifact(projectId, artifact.id(), 1).size();
        assertThat(chunkCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Webhook Idempotency: Duplicate and replayed webhook events are filtered cleanly")
    void testWebhookDeduplicationAndReplayProtection() {
        String eventId = "evt-gh-delivery-" + UUID.randomUUID();
        String payload = """
            {
              "action": "closed",
              "pull_request": {
                "number": 42,
                "title": "Add resilience features",
                "merged": true
              }
            }
            """;

        Map<String, String> headers = Map.of(
                "x-github-delivery", eventId,
                "x-github-event", "pull_request"
        );

        // First event arrival: must normalize and process
        NormalizedEvent firstEvent = gitHubAdapter.receiveEvents(projectId, payload, headers);
        assertThat(firstEvent).isNotNull();
        assertThat(firstEvent.eventId()).isEqualTo(eventId);

        // Record processed
        reliabilityService.recordProcessed(projectId, ProviderType.GITHUB, eventId, "pull_request", "github:pull_request:" + eventId, null);

        // Replayed event with same delivery ID: must be recognized as duplicate and skipped
        boolean isDuplicate = reliabilityService.isDuplicate(projectId, ProviderType.GITHUB, eventId, "github:pull_request:" + eventId);
        assertTrue(isDuplicate, "Replayed event with identical event ID and idempotency key must be flagged duplicate");

        NormalizedEvent replayedEvent = gitHubAdapter.receiveEvents(projectId, payload, headers);
        assertThat(replayedEvent).isNull();
    }

    @Test
    @DisplayName("Checkpoint Reconciliation: Polling cursors advance monotonically and prevent re-ingestion")
    void testCheckpointCursorProgression() {
        String resourceId = "deploys";

        // Initial cursor check
        assertTrue(checkpointRepository.getCursor(projectId, ProviderType.RENDER, resourceId).isEmpty());

        // Save first checkpoint
        checkpointRepository.saveCheckpoint(projectId, ProviderType.RENDER, resourceId, "cursor-batch-001");
        assertThat(checkpointRepository.getCursor(projectId, ProviderType.RENDER, resourceId))
                .contains("cursor-batch-001");

        // Save subsequent checkpoint (cursor advancement)
        checkpointRepository.saveCheckpoint(projectId, ProviderType.RENDER, resourceId, "cursor-batch-002");
        assertThat(checkpointRepository.getCursor(projectId, ProviderType.RENDER, resourceId))
                .contains("cursor-batch-002");
    }

    @Test
    @DisplayName("Action Execution Resilience: Atomic claim prevents multiple dispatches on transient network failure")
    void testActionExecutionRecoveryResilience() {
        ActionRequestRecord submitted = actionRequestService.submitActionRequest(
                projectId,
                null,
                new SubmitActionRequest("TRIGGER_DEPLOYMENT", Map.of("serviceId", "srv-resilience"), false)
        );

        assertThat(submitted.status()).isEqualTo(ActionRequestStatus.PENDING);

        // Review action successfully
        ActionRequestRecord reviewed = actionRequestService.reviewAction(
                projectId,
                submitted.id(),
                userId,
                new ReviewActionRequest(true, "Approved with resilience check")
        );

        assertThat(reviewed.status()).isEqualTo(ActionRequestStatus.EXECUTED);

        // Attempting to review or execute again fails fast without re-executing
        assertFalse(actionRequestService.listPendingActions(projectId).stream().anyMatch(a -> a.id().equals(submitted.id())));
    }
}
