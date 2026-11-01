package com.context_platform.aether.security;

import com.context_platform.aether.common.ProjectAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProjectSecurityCheckerRegressionTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private RlsContextHelper rlsContextHelper;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<String> mappedQuerySpec;

    private ProjectSecurityChecker securityChecker;

    @BeforeEach
    void setUp() {
        securityChecker = new ProjectSecurityChecker(jdbcClient, rlsContextHelper);
    }

    @Test
    @DisplayName("Regression Fix: verifyProjectAccess establishes RLS project context BEFORE querying project_members")
    void testVerifyProjectAccessSetsRlsContextBeforeQuery() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of("OWNER"));

        // Must succeed without exception
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        // Verify ordering: setProjectContext MUST happen before the SQL query execution
        InOrder inOrder = inOrder(rlsContextHelper, jdbcClient, mappedQuerySpec);
        inOrder.verify(rlsContextHelper).setProjectContext(projectId);
        inOrder.verify(jdbcClient).sql(contains("FROM project_members"));
        inOrder.verify(mappedQuerySpec).optional();
        verify(rlsContextHelper, never()).setProjectContext(null);
    }

    @Test
    @DisplayName("Security: verifyProjectAccess clears RLS context and throws when user is not a member")
    void testVerifyProjectAccessRejectsNonMemberAndClearsContext() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.empty()); // Non-member

        assertThatThrownBy(() -> securityChecker.verifyProjectAccess(projectId, userId, "VIEWER"))
            .isInstanceOf(ProjectAccessDeniedException.class)
            .hasMessageContaining("User is not a member of project: " + projectId);

        InOrder inOrder = inOrder(rlsContextHelper);
        inOrder.verify(rlsContextHelper).setProjectContext(projectId);
        inOrder.verify(rlsContextHelper).setProjectContext(null);
    }

    @Test
    @DisplayName("Security: verifyProjectAccess clears RLS context and throws when role is insufficient")
    void testVerifyProjectAccessRejectsInsufficientRoleAndClearsContext() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(any())).thenReturn(statementSpec);
        when(statementSpec.query(String.class)).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(Optional.of("VIEWER")); // Only VIEWER

        assertThatThrownBy(() -> securityChecker.verifyProjectAccess(projectId, userId, "OWNER"))
            .isInstanceOf(ProjectAccessDeniedException.class)
            .hasMessageContaining("Insufficient role. Required: OWNER, current: VIEWER");

        InOrder inOrder = inOrder(rlsContextHelper);
        inOrder.verify(rlsContextHelper).setProjectContext(projectId);
        inOrder.verify(rlsContextHelper).setProjectContext(null);
    }

    @Test
    @DisplayName("Security: verifyProjectAccess rejects null userId immediately")
    void testVerifyProjectAccessRejectsNullUserId() {
        UUID projectId = UUID.randomUUID();

        assertThatThrownBy(() -> securityChecker.verifyProjectAccess(projectId, null, "VIEWER"))
            .isInstanceOf(ProjectAccessDeniedException.class)
            .hasMessageContaining("Authentication required");

        verifyNoInteractions(rlsContextHelper);
        verifyNoInteractions(jdbcClient);
    }
}
