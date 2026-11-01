package com.context_platform.aether.data.service;

import com.context_platform.aether.artifact.Artifact;
import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.artifact.ArtifactVersion;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ResourceNotFoundException;
import com.context_platform.aether.data.model.ProjectExportArchive;
import com.context_platform.aether.data.model.ProjectExportArchive.ArtifactWithVersions;
import com.context_platform.aether.data.model.ProjectExportArchive.DecisionWithVersions;
import com.context_platform.aether.decision.DecisionRepository;
import com.context_platform.aether.decision.DecisionVersion;
import com.context_platform.aether.decision.TechnicalDecision;
import com.context_platform.aether.event.AuditService;
import com.context_platform.aether.event.TimelineEvent;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.project.Project;
import com.context_platform.aether.project.ProjectRepository;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.project.ProjectService;
import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.storage.StorageService;
import com.context_platform.aether.workflow.model.ActionRequestRecord;
import com.context_platform.aether.workflow.model.TaskRecord;
import com.context_platform.aether.workflow.repository.ActionRequestRepository;
import com.context_platform.aether.workflow.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DataManagementService {

    private static final Logger log = LoggerFactory.getLogger(DataManagementService.class);

    private final ProjectSecurityChecker securityChecker;
    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final ArtifactRepository artifactRepository;
    private final DecisionRepository decisionRepository;
    private final TimelineService timelineService;
    private final TaskRepository taskRepository;
    private final ActionRequestRepository actionRequestRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final JdbcClient jdbcClient;

    public DataManagementService(
        ProjectSecurityChecker securityChecker,
        ProjectService projectService,
        ProjectRepository projectRepository,
        ArtifactRepository artifactRepository,
        DecisionRepository decisionRepository,
        TimelineService timelineService,
        TaskRepository taskRepository,
        ActionRequestRepository actionRequestRepository,
        StorageService storageService,
        AuditService auditService,
        JdbcClient jdbcClient
    ) {
        this.securityChecker = securityChecker;
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.artifactRepository = artifactRepository;
        this.decisionRepository = decisionRepository;
        this.timelineService = timelineService;
        this.taskRepository = taskRepository;
        this.actionRequestRepository = actionRequestRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public ProjectExportArchive exportProjectData(UUID projectId, UUID userId, String userEmail) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        ProjectResponse project = projectService.getProject(projectId, userId);

        // 1. Artifacts and versions
        List<Artifact> artifacts = artifactRepository.listArtifacts(projectId, null, null, null);
        List<ArtifactWithVersions> exportArtifacts = new ArrayList<>();
        for (Artifact art : artifacts) {
            List<ArtifactVersion> versions = artifactRepository.listVersions(art.id(), projectId);
            exportArtifacts.add(new ArtifactWithVersions(art, versions));
        }

        // 2. Decisions and versions
        List<TechnicalDecision> decisions = decisionRepository.listDecisions(projectId, null, null);
        List<DecisionWithVersions> exportDecisions = new ArrayList<>();
        for (TechnicalDecision dec : decisions) {
            List<DecisionVersion> versions = decisionRepository.listVersions(dec.id(), projectId);
            exportDecisions.add(new DecisionWithVersions(dec, versions));
        }

        // 3. Timeline events
        List<TimelineEvent> timeline = timelineService.listTimeline(projectId, null, null, 5000);

        // 4. Tasks
        List<TaskRecord> tasks = taskRepository.findByProject(projectId, null, 5000, 0);

        // 5. Action Requests
        List<ActionRequestRecord> actions = actionRequestRepository.findByProject(projectId, null);

        auditService.log(projectId, userId, "PROJECT_DATA_EXPORTED", "HUMAN_USER", null, null,
            "{\"userEmail\":\"" + userEmail + "\",\"artifactCount\":" + exportArtifacts.size() + "}");

        return new ProjectExportArchive(
            project,
            exportArtifacts,
            exportDecisions,
            timeline,
            tasks,
            actions,
            Instant.now(),
            userEmail
        );
    }

    @Transactional
    public void deleteProjectData(UUID projectId, UUID userId, String confirmationCode) {
        securityChecker.verifyProjectAccess(projectId, userId, "OWNER");

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        String expectedCode = "CONFIRM_DELETE_" + project.publicId();
        if (!expectedCode.equals(confirmationCode)) {
            throw new ApiException(
                "Invalid confirmation code. To permanently delete this project, confirm with: " + expectedCode,
                HttpStatus.BAD_REQUEST
            );
        }

        log.warn("PERMANENT PROJECT PURGE INITIATED: Project {} ({}) by user {}", project.id(), project.publicId(), userId);

        // Allow project purge in transaction to bypass immutable triggers
        jdbcClient.sql("SET LOCAL aether.allow_project_purge = 'on'").update();

        // Cascading deletion removes all child entities (artifacts, chunks, embeddings, decisions, tasks, timeline, etc.)
        int deleted = jdbcClient.sql("DELETE FROM projects WHERE id = ?").param(projectId).update();
        if (deleted == 0) {
            throw new ResourceNotFoundException("Project", projectId);
        }

        // Remove disk storage files
        storageService.deleteProjectData(projectId);

        log.info("PERMANENT PROJECT PURGE COMPLETED: Project {} successfully purged from system.", projectId);
    }
}
