package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.auth.AuthService;
import com.context_platform.aether.decision.DecisionService;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.project.ProjectService;
import com.context_platform.aether.security.RlsContextHelper;
import com.context_platform.aether.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AetherApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class BaseTest {

    @Autowired
    protected JdbcClient jdbcClient;

    @Autowired
    protected RlsContextHelper rlsContextHelper;

    @Autowired
    protected AuthService authService;

    @Autowired
    protected ProjectService projectService;

    @Autowired
    protected ArtifactService artifactService;

    @Autowired
    protected DecisionService decisionService;

    @Autowired
    protected TimelineService timelineService;

    @Autowired
    protected StorageService storageService;

    @BeforeEach
    void setUpRls() {
        rlsContextHelper.setBypassRls(true);
    }

    protected com.context_platform.aether.auth.AuthResponse registerUser(String email, String password, String name) {
        String uniqueEmail = java.util.UUID.randomUUID().toString().substring(0, 8) + "_" + email;
        return authService.register(new com.context_platform.aether.auth.RegisterRequest(uniqueEmail, password, name), "127.0.0.1", "TestClient");
    }

    protected com.context_platform.aether.project.ProjectResponse createTestProject(String name, java.util.UUID userId) {
        return projectService.createProject(
            new com.context_platform.aether.project.CreateProjectRequest(name, "Test description", "BACKEND", java.util.List.of("Java"), "/workspace/" + name.toLowerCase().replace(" ", "_"), "LOCAL_FIRST"),
            userId,
            "OWNER"
        );
    }
}
