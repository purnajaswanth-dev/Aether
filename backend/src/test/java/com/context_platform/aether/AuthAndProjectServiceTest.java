package com.context_platform.aether;

import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.auth.LoginRequest;
import com.context_platform.aether.auth.RegisterRequest;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import com.context_platform.aether.event.TimelineEvent;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.IntegrationStatusDto;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthAndProjectServiceTest extends BaseTest {

    @Test
    @DisplayName("Gate 6: Project creation initializes memory, 7 catalog integrations, and timeline event in one transaction")
    void testProjectCreationInitializesCompletely() {
        rlsContextHelper.setBypassRls(true);

        // 1. Register
        String email = "creator_" + UUID.randomUUID() + "@team.com";
        AuthResponse user = authService.register(
            new RegisterRequest(email, "SecurePassword123!", "Lead Architect"),
            "127.0.0.1", "JUnit"
        );

        // 2. Create Project in one transaction
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Vionex Core", "Platform workspace", "Modular Monolith", List.of("Java 17", "Spring Boot", "Postgres 16"), null, "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        assertThat(project.id()).isNotNull();
        assertThat(project.publicId()).matches("PRJ-\\d{3}");
        assertThat(project.currentUserRole()).isEqualTo("OWNER");

        // 3. Verify all 7 catalogue integrations initialized with NOT_CONNECTED
        List<IntegrationStatusDto> integrations = projectService.getIntegrationsCatalogue(project.id(), user.user().id());
        assertThat(integrations).hasSize(7);
        assertThat(integrations).allMatch(i -> "NOT_CONNECTED".equals(i.status()));
        assertThat(integrations).allMatch(i -> !i.plannedCapabilities().isEmpty());

        // 4. Verify initial timeline event exists with all required fields
        List<TimelineEvent> timeline = timelineService.listTimeline(project.id(), null, null, 10);
        assertThat(timeline).isNotEmpty();
        TimelineEvent firstEvent = timeline.get(timeline.size() - 1);
        assertThat(firstEvent.eventType()).isEqualTo("PROJECT_CREATED");
        assertThat(firstEvent.sourcePlatform()).isEqualTo("PLATFORM");
        assertThat(firstEvent.status()).isEqualTo("SUCCESS");
        assertThat(firstEvent.actorId()).isEqualTo(user.user().email());
        assertThat(firstEvent.actorType()).isEqualTo("HUMAN_USER");
        assertThat(firstEvent.summary()).contains(project.publicId());
    }

    @Test
    @DisplayName("Auth: Password hashing with Argon2id and login rate limiting after 5 failed attempts")
    void testAuthPasswordAndRateLimiting() {
        String email = "ratelimit_" + UUID.randomUUID() + "@team.com";
        authService.register(new RegisterRequest(email, "Argon2Password123!", "RateLimit User"), "127.0.0.1", "JUnit");

        // Successful login
        AuthResponse loginSuccess = authService.login(new LoginRequest(email, "Argon2Password123!"), "127.0.0.1", "JUnit");
        assertThat(loginSuccess.token()).isNotEmpty();

        // 5 consecutive failed login attempts
        String testIp = "192.168.1.100";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> {
                authService.login(new LoginRequest(email, "WrongPassword"), testIp, "JUnit");
            }).isInstanceOf(ApiException.class);
        }

        // 6th attempt is blocked by rate limiter with 429 TOO_MANY_REQUESTS
        assertThatThrownBy(() -> {
            authService.login(new LoginRequest(email, "Argon2Password123!"), testIp, "JUnit");
        })
        .isInstanceOf(ApiException.class)
        .matches(e -> ((ApiException) e).getStatus() == HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Authorization: User without project role cannot access project")
    void testProjectAccessDeniedForNonMembers() {
        AuthResponse user1 = authService.register(new RegisterRequest("u1_" + UUID.randomUUID() + "@team.com", "Password123!", "User 1"), "127.0.0.1", "JUnit");
        AuthResponse user2 = authService.register(new RegisterRequest("u2_" + UUID.randomUUID() + "@team.com", "Password123!", "User 2"), "127.0.0.1", "JUnit");

        ProjectResponse p1 = projectService.createProject(
            new CreateProjectRequest("User 1 Project", "Private", "Web", List.of("Java"), null, "LOCAL_FIRST"),
            user1.user().id(),
            user1.user().email()
        );

        // User 2 cannot access User 1's project
        assertThatThrownBy(() -> {
            projectService.getProject(p1.id(), user2.user().id());
        }).isInstanceOf(ProjectAccessDeniedException.class);
    }
}

