package com.context_platform.aether;

import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.auth.RegisterRequest;
import com.context_platform.aether.common.ActorType;
import com.context_platform.aether.common.OptimisticLockingException;
import com.context_platform.aether.decision.CreateDecisionRequest;
import com.context_platform.aether.decision.DecisionActionRequest;
import com.context_platform.aether.decision.DecisionResponse;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class DecisionOptimisticLockingConcurrencyTest extends BaseTest {

    @Test
    @DisplayName("Gate 5: Two concurrent approvals: exactly one succeeds via optimistic locking")
    void testConcurrentDecisionApprovals() throws InterruptedException {
        rlsContextHelper.setBypassRls(true);

        // 1. Setup user & project
        AuthResponse human = authService.register(
            new RegisterRequest("concurrency_" + UUID.randomUUID() + "@team.com", "Password123!", "Concurrency Tester"),
            "127.0.0.1", "JUnit"
        );
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Concurrency Project", "Testing locks", "Web", List.of("Java"), null, "LOCAL_FIRST"),
            human.user().id(),
            human.user().email()
        );

        setSecurityContext(human.user().id(), human.user().email(), ActorType.HUMAN_USER);

        // 2. Create decision and move to UNDER_REVIEW
        DecisionResponse dec = decisionService.createDecision(
            project.id(),
            new CreateDecisionRequest("Concurrent DB Choice", "DATABASE", "Pick DB", "Use PostgreSQL 16", "Solid", "None", null),
            human.user().id()
        );

        DecisionResponse reviewDec = decisionService.submitForReview(
            project.id(),
            dec.id(),
            new DecisionActionRequest(dec.lockVersion(), "Ready for review", null, null),
            human.user().id()
        );

        int targetLockVersion = reviewDec.lockVersion();

        // 3. Concurrently execute two approvals with the exact same expectedLockVersion
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger optimisticLockFailures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    setSecurityContext(human.user().id(), human.user().email(), ActorType.HUMAN_USER);
                    startLatch.await(); // Synchronize start of both threads

                    decisionService.approveDecision(
                        project.id(),
                        dec.id(),
                        new DecisionActionRequest(targetLockVersion, "Approval from thread " + threadNum, null, null),
                        human.user().id()
                    );
                    successes.incrementAndGet();
                } catch (OptimisticLockingException e) {
                    optimisticLockFailures.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire both threads concurrently!
        doneLatch.await();
        executor.shutdown();

        // 4. Assert: Exactly ONE approval succeeded, and exactly ONE failed with OptimisticLockingException
        assertThat(successes.get()).isEqualTo(1);
        assertThat(optimisticLockFailures.get()).isEqualTo(1);

        // Verify final state in DB
        DecisionResponse finalDec = decisionService.getDecision(project.id(), dec.id(), human.user().id()).decision();
        assertThat(finalDec.state()).isEqualTo("APPROVED");
        assertThat(finalDec.lockVersion()).isEqualTo(targetLockVersion + 1);
    }

    private void setSecurityContext(UUID userId, String email, ActorType actorType) {
        UserPrincipal principal = new UserPrincipal(userId, email, email, actorType);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

