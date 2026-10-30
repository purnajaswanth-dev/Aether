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
}
