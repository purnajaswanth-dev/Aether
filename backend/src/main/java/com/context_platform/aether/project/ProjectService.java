package com.context_platform.aether.project;

import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ResourceNotFoundException;
import com.context_platform.aether.event.AuditService;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.security.RlsContextHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TimelineService timelineService;
    private final AuditService auditService;
    private final ProjectSecurityChecker securityChecker;
    private final RlsContextHelper rlsContextHelper;
    private final ObjectMapper objectMapper;

    public ProjectService(
        ProjectRepository projectRepository,
        TimelineService timelineService,
        AuditService auditService,
        ProjectSecurityChecker securityChecker,
        RlsContextHelper rlsContextHelper,
        ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.timelineService = timelineService;
        this.auditService = auditService;
        this.securityChecker = securityChecker;
        this.rlsContextHelper = rlsContextHelper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, UUID creatorUserId, String userEmail) {
        String publicId = projectRepository.nextPublicId();

        Project project = projectRepository.createProject(
            publicId,
            request.name(),
            request.description(),
            request.projectType(),
            request.techStack(),
            request.directory(),
            request.storagePreference()
        );

        // Add creator as OWNER
        projectRepository.addMember(project.id(), creatorUserId, ProjectRole.OWNER.name());

        // Initialize catalogue integrations & permissions (all NOT_CONNECTED)
        projectRepository.initializeCatalogueIntegrations(project.id());

        // Initialize first timeline event in the same transaction
        timelineService.append(
            project.id(),
            null,
            "PLATFORM",
            "PROJECT_CREATED",
            null,
            null,
            null,
            "SUCCESS",
            userEmail,
            "HUMAN_USER",
            "Project '" + project.name() + "' (" + project.publicId() + ") initialized with persistent memory and core schemas.",
            "{\"publicId\":\"" + project.publicId() + "\",\"storagePreference\":\"" + project.storagePreference() + "\"}"
        );

        // Audit log
        auditService.log(project.id(), creatorUserId, "PROJECT_CREATED", "HUMAN_USER", null, null, "{\"name\":\"" + project.name() + "\"}");

        List<String> techStack = deserializeJsonList(project.techStack());
        return new ProjectResponse(
            project.id(),
            project.publicId(),
            project.name(),
            project.description(),
            project.projectType(),
            techStack,
            project.directory(),
            project.storagePreference(),
            project.archived(),
            ProjectRole.OWNER.name(),
            project.createdAt(),
            project.updatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(UUID userId) {
        return projectRepository.listByUser(userId);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        List<String> techStack = deserializeJsonList(project.techStack());
        return new ProjectResponse(
            project.id(),
            project.publicId(),
            project.name(),
            project.description(),
            project.projectType(),
            techStack,
            project.directory(),
            project.storagePreference(),
            project.archived(),
            "MEMBER",
            project.createdAt(),
            project.updatedAt()
        );
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "OWNER");

        Project existing = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        projectRepository.updateProject(
            projectId,
            request.name(),
            request.description(),
            request.projectType(),
            request.techStack(),
            request.directory(),
            request.storagePreference()
        );

        auditService.log(projectId, userId, "PROJECT_UPDATED", "HUMAN_USER", null, null, "{\"name\":\"" + request.name() + "\"}");
        return getProject(projectId, userId);
    }

    @Transactional
    public void archiveProject(UUID projectId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "OWNER");
        projectRepository.archiveProject(projectId);
        auditService.log(projectId, userId, "PROJECT_ARCHIVED", "HUMAN_USER", null, null, "{}");
    }

    public List<IntegrationStatusDto> getIntegrationsCatalogue(UUID projectId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        // Honest catalogue listing of providers per Constitution Non-negotiable Rule 9
        return List.of(
            new IntegrationStatusDto(
                UUID.randomUUID(), "claude", "Claude (Anthropic)", "AI Agent",
                "Model Context Protocol remote integration for Claude Desktop and Claude Code.",
                "NOT_CONNECTED",
                List.of("Retrieve context (read-only)", "Save review findings", "Propose decisions"),
                "MCP server connectivity planned for Phase 2."
            ),
            new IntegrationStatusDto(
                UUID.randomUUID(), "chatgpt", "ChatGPT (OpenAI)", "AI Agent",
                "Custom GPT / MCP actions connector for structured context exchange.",
                "NOT_CONNECTED",
                List.of("Retrieve architecture & context", "Propose decisions"),
                "MCP server connectivity planned for Phase 2."
            ),
            new IntegrationStatusDto(
                UUID.randomUUID(), "antigravity", "Google Antigravity", "AI Agent",
                "Deep engineering agent integration for architecture, coding, and workflow synchronization.",
                "NOT_CONNECTED",
                List.of("Bidirectional context sync", "Full memory navigation", "Task handoff"),
                "MCP server & IDE rules generation planned for Phase 2."
            ),
            new IntegrationStatusDto(
                UUID.randomUUID(), "github", "GitHub", "Source Control & CI",
                "Automated commit, PR, and GitHub Actions workflow event capture.",
                "NOT_CONNECTED",
                List.of("Push/commit tracking", "PR metadata", "CI workflow status"),
                "GitHub App integration planned for Phase 4."
            ),
            new IntegrationStatusDto(
                UUID.randomUUID(), "render", "Render", "Cloud Hosting",
                "Deployment monitoring, build log capture, and runtime failure extraction.",
                "NOT_CONNECTED",
                List.of("Deploy status tracking", "Build failure logs", "Runtime diagnostics"),
                "Render API adapter planned for Phase 4."
            ),
            new IntegrationStatusDto(
                UUID.randomUUID(), "postman", "Postman", "API Platform",
                "API specification and test run result synchronization.",
                "NOT_CONNECTED",
                List.of("API specs", "Collection test runs"),
                "Postman connector planned for Phase 4."
            ),
            new IntegrationStatusDto(
                UUID.randomUUID(), "docker", "Docker", "Containerization",
                "Local container builds, Dockerfile parsing, and run inspection.",
                "NOT_CONNECTED",
                List.of("Dockerfile metadata", "Local build results"),
                "Local agent Docker collector planned for Phase 4."
            )
        );
    }

    public List<Map<String, Object>> getPermissions(UUID projectId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");
        return projectRepository.getPermissions(projectId);
    }

    private List<String> deserializeJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}

