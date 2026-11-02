package com.context_platform.aether.security;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import com.context_platform.aether.event.TimelineController;
import com.context_platform.aether.event.TimelineEvent;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.workflow.controller.ActionRequestController;
import com.context_platform.aether.workflow.controller.TaskController;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.ActionRequestStatus;
import com.context_platform.aether.workflow.model.TaskPriority;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.model.TaskStatus;
import com.context_platform.aether.workflow.repository.AgentHandoffRepository;
import com.context_platform.aether.workflow.repository.TaskEventRepository;
import com.context_platform.aether.workflow.service.ActionRequestService;
import com.context_platform.aether.workflow.service.AgentTrackingService;
import com.context_platform.aether.workflow.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductionIncidentAuthorizationRegressionTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private RlsContextHelper rlsContextHelper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<String> mappedQuerySpec;

    @Mock
    private TimelineService timelineService;

    @Mock
    private ActionRequestService actionRequestService;

    @Mock
    private TaskService taskService;

    @Mock
    private AgentTrackingService agentTrackingService;

    @Mock
    private TaskEventRepository taskEventRepository;

    @Mock
    private AgentHandoffRepository handoffRepository;

    private ProjectSecurityChecker securityChecker;
    private TimelineController timelineController;
    private ActionRequestController actionRequestController;
    private TaskController taskController;

    private final UUID projectId = UUID.fromString("25d352d5-c611-4133-bb44-9d150a7c7244");
    private final UUID ownerId = UUID.fromString("b0cc2558-a471-4a85-be11-7a3c955bffdb");
    private final UUID memberId = UUID.randomUUID();
    private final UUID nonMemberId = UUID.fromString("8caa8059-31bf-4cfd-b790-3d184241b93b");

    @BeforeEach
    void setUp() {
        securityChecker = new ProjectSecurityChecker(jdbcClient, rlsContextHelper);
        timelineController = new TimelineController(timelineService, securityChecker);
        actionRequestController = new ActionRequestController(actionRequestService, securityChecker);
        taskController = new TaskController(taskService, agentTrackingService, taskEventRepository, handoffRepository, securityChecker);
    }

    @Test
    @DisplayName("Transactional Verification: verifyProjectAccess and getTimeline are transactional")
    void testTransactionalAnnotationsPresent() throws Exception {
        Method verifyMethod = ProjectSecurityChecker.class.getMethod("verifyProjectAccess", UUID.class, UUID.class, String.class);
        assertThat(verifyMethod.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(verifyMethod.getAnnotation(Transactional.class).readOnly()).isTrue();

        Method timelineMethod = TimelineController.class.getMethod("getTimeline", UUID.class, String.class, String.class, int.class, UserPrincipal.class);
        assertThat(timelineMethod.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(timelineMethod.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    @DisplayName("Requirement: Project owner can access their project timeline")
    void testProjectOwnerCanAccessTimeline() {
        UserPrincipal ownerPrincipal = new UserPrincipal(ownerId, "hi@gmail.com", "hi", com.context_platform.aether.common.ActorType.HUMAN_USER);

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of("OWNER"));

        TimelineEvent event = new TimelineEvent(
                UUID.randomUUID(), projectId, null, "PLATFORM", Instant.now(), "PROJECT_CREATED",
                null, null, null, "SUCCESS", ownerPrincipal.email(), "HUMAN_USER", "Summary", "{}", Instant.now()
        );
        when(timelineService.listTimeline(projectId, null, null, 100)).thenReturn(List.of(event));

        ResponseEntity<List<TimelineEvent>> response = timelineController.getTimeline(projectId, null, null, 100, ownerPrincipal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(rlsContextHelper).setProjectContext(projectId);
    }

    @Test
    @DisplayName("Requirement: Valid project member can access actions")
    void testProjectMemberCanAccessActions() {
        UserPrincipal memberPrincipal = new UserPrincipal(memberId, "member@example.com", "Member", com.context_platform.aether.common.ActorType.HUMAN_USER);

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of("MEMBER"));

        ActionRequestRecord action = new ActionRequestRecord(
                UUID.randomUUID(), projectId, null, "RUN_TESTS", Map.of(),
                false, ActionRequestStatus.PENDING, null, null, null, null, null, 1, null, Instant.now(), null
        );
        when(actionRequestService.listAllActions(projectId, ActionRequestStatus.PENDING)).thenReturn(List.of(action));

        ResponseEntity<List<ActionRequestRecord>> response = actionRequestController.listActions(projectId, ActionRequestStatus.PENDING, memberPrincipal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Requirement: Non-member is rejected with ProjectAccessDeniedException (403)")
    void testNonMemberCannotAccessProject() {
        UserPrincipal nonMemberPrincipal = new UserPrincipal(nonMemberId, "hi6303423@gmail.com", "jaswanth", com.context_platform.aether.common.ActorType.HUMAN_USER);

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.empty()); // Not a member of project 25d352d5

        assertThatThrownBy(() -> timelineController.getTimeline(projectId, null, null, 100, nonMemberPrincipal))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessageContaining("User is not a member of project: " + projectId);

        assertThatThrownBy(() -> actionRequestController.listActions(projectId, ActionRequestStatus.PENDING, nonMemberPrincipal))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessageContaining("User is not a member of project: " + projectId);
    }

    @Test
    @DisplayName("Requirement: Cross-project access remains blocked between different projects")
    void testCrossProjectAccessBlocked() {
        UUID otherProjectId = UUID.fromString("4ba117c1-841f-4ebe-802c-ac2157d0d1b1");
        // User is owner of 25d352d5 but NOT 4ba117c1
        UserPrincipal userOfProjectA = new UserPrincipal(ownerId, "hi@gmail.com", "hi", com.context_platform.aether.common.ActorType.HUMAN_USER);

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> securityChecker.verifyProjectAccess(otherProjectId, userOfProjectA.id(), "VIEWER"))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessageContaining("User is not a member of project: " + otherProjectId);
    }

    @Test
    @DisplayName("Requirement: Tasks endpoint routes properly and accepts UserPrincipal")
    void testTasksEndpointSuccess() {
        UserPrincipal ownerPrincipal = new UserPrincipal(ownerId, "hi@gmail.com", "hi", com.context_platform.aether.common.ActorType.HUMAN_USER);

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of("OWNER"));

        TaskRecord task = new TaskRecord(
                UUID.randomUUID(), projectId, 1, "Implement Auth", "Description",
                TaskStatus.BACKLOG, TaskPriority.HIGH, ownerId, false, null, null, 1, ownerId, Instant.now(), Instant.now()
        );
        when(taskService.listTasks(projectId, null, 50, 0)).thenReturn(List.of(task));

        ResponseEntity<List<TaskRecord>> response = taskController.listTasks(projectId, null, 50, 0, ownerPrincipal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Requirement: Invalid JWT / Null User ID is rejected")
    void testNullUserRejected() {
        assertThatThrownBy(() -> securityChecker.verifyProjectAccess(projectId, null, "VIEWER"))
                .isInstanceOf(ProjectAccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }
}
