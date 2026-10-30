package com.context_platform.aether.security;

import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ActorType;
import com.context_platform.aether.common.ProjectAccessDeniedException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ProjectSecurityChecker {

    private final JdbcClient jdbcClient;

    public ProjectSecurityChecker(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ActorContext getCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return new ActorContext(principal.email(), principal.actorType(), principal.id());
        }
        return ActorContext.system();
    }

    public void verifyProjectAccess(UUID projectId, UUID userId, String requiredRole) {
        if (userId == null) {
            throw new ProjectAccessDeniedException("Authentication required for project access");
        }

        Optional<String> userRole = jdbcClient.sql("""
            SELECT role FROM project_members
            WHERE project_id = ? AND user_id = ?
        """)
        .param(projectId)
        .param(userId)
        .query(String.class)
        .optional();

        if (userRole.isEmpty()) {
            throw new ProjectAccessDeniedException("User is not a member of project: " + projectId);
        }

        if (requiredRole != null && !hasSufficientRole(userRole.get(), requiredRole)) {
            throw new ProjectAccessDeniedException("Insufficient role. Required: " + requiredRole + ", current: " + userRole.get());
        }
    }

    private boolean hasSufficientRole(String currentRole, String requiredRole) {
        if ("OWNER".equalsIgnoreCase(currentRole)) {
            return true;
        }
        if ("MEMBER".equalsIgnoreCase(currentRole)) {
            return !"OWNER".equalsIgnoreCase(requiredRole);
        }
        if ("VIEWER".equalsIgnoreCase(currentRole)) {
            return "VIEWER".equalsIgnoreCase(requiredRole);
        }
        return false;
    }
}
