package com.context_platform.aether.integration;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.integration.model.AgentDeviceRecord;
import com.context_platform.aether.integration.model.AgentFileMetadataRecord;
import com.context_platform.aether.integration.service.AgentService;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAgentIntegrationTest extends BaseTest {

    @Autowired
    private AgentService agentService;

    @Test
    @DisplayName("Local Agent: pairing, token authentication, file sync, and heartbeat lifecycle")
    void testAgentLifecycle() {
        AuthResponse user = registerUser("agent_dev@example.com", "Password123!", "Local Agent Developer");
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Local Workspace Proj", "Local agent pairing test", "WEB_APP", List.of("Java"), "/dir", "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        // 1. Agent initializes pairing
        AgentService.PairInitResponse initResp = agentService.initPairing(project.id());
        assertThat(initResp.deviceCode()).startsWith("dc_");
        assertThat(initResp.userCode()).hasSize(9); // ABCD-1234 format

        // Polling returns pending initially
        AgentService.PairPollResponse pendingPoll = agentService.pollPairing(initResp.deviceCode());
        assertThat(pendingPoll.status()).isEqualTo("PENDING_PAIR");

        // 2. User confirms pairing from UI
        String rawToken = agentService.confirmPairing(
            project.id(),
            initResp.userCode(),
            "macbook-pro",
            "darwin-arm64",
            "/Users/developer/code/project",
            user.user().id()
        );
        assertThat(rawToken).startsWith("aeth_agent_");

        // Polling now returns ACTIVE
        AgentService.PairPollResponse activePoll = agentService.pollPairing(initResp.deviceCode());
        assertThat(activePoll.status()).isEqualTo("ACTIVE");

        // 3. Authenticate agent device using Bearer rawToken
        AgentDeviceRecord authenticatedDevice = agentService.authenticateAgent("Bearer " + rawToken);
        assertThat(authenticatedDevice.hostname()).isEqualTo("macbook-pro");
        assertThat(authenticatedDevice.status()).isEqualTo("ACTIVE");

        // 4. Send Heartbeat
        agentService.heartbeat(authenticatedDevice);

        // 5. Sync indexed files
        AgentFileMetadataRecord file1 = new AgentFileMetadataRecord(
            null,
            project.id(),
            authenticatedDevice.id(),
            "backend/src/main/java/com/example/AuthFilter.java",
            "hash_auth_filter",
            4096,
            Instant.now(),
            "Java",
            List.of("AuthFilter", "doFilterInternal"),
            Instant.now()
        );
        agentService.syncFileMetadata(authenticatedDevice, List.of(file1));

        // 6. Search relevant files
        List<AgentFileMetadataRecord> found = agentService.findRelevantFiles(project.id(), "AuthFilter", 10);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).relativePath()).isEqualTo("backend/src/main/java/com/example/AuthFilter.java");

        // 7. Disconnect
        agentService.disconnectDevice(project.id(), authenticatedDevice.id(), user.user().id());
        List<AgentDeviceRecord> devices = agentService.listDevices(project.id(), user.user().id());
        assertThat(devices.get(0).status()).isEqualTo("DISCONNECTED");
    }
}
