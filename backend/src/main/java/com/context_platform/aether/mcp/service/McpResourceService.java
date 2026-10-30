package com.context_platform.aether.mcp.service;

import com.context_platform.aether.artifact.Artifact;
import com.context_platform.aether.artifact.ArtifactRepository;
import com.context_platform.aether.artifact.ArtifactService;
import com.context_platform.aether.artifact.ArtifactVersion;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.mcp.model.McpResourceDefinition;
import com.context_platform.aether.mcp.model.McpToken;
import com.context_platform.aether.mcp.security.McpSecurityService;
import com.context_platform.aether.project.Project;
import com.context_platform.aether.project.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpResourceService {

    private final ProjectRepository projectRepository;
    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final McpSecurityService securityService;

    public McpResourceService(
        ProjectRepository projectRepository,
        ArtifactRepository artifactRepository,
        ArtifactService artifactService,
        McpSecurityService securityService
    ) {
        this.projectRepository = projectRepository;
        this.artifactRepository = artifactRepository;
        this.artifactService = artifactService;
        this.securityService = securityService;
    }

    public List<McpResourceDefinition> listResources(McpToken token) {
        securityService.enforcePermission(token, "READ_PROJECT_CONTEXT");
        String baseUri = "aether://project/" + token.projectId();
        return List.of(
            new McpResourceDefinition(
                baseUri + "/overview",
                "Project Overview",
                "Complete project summary, tech stack, and constitutional status",
                "text/markdown"
            ),
            new McpResourceDefinition(
                baseUri + "/architecture",
                "Current Architecture",
                "Current approved system architecture document",
                "text/markdown"
            )
        );
    }

    public Map<String, Object> readResource(String uri, McpToken token) {
        securityService.enforcePermission(token, "READ_PROJECT_CONTEXT");
        String overviewUri = "aether://project/" + token.projectId() + "/overview";
        String archUri = "aether://project/" + token.projectId() + "/architecture";

        if (uri.equalsIgnoreCase(overviewUri)) {
            Project project = projectRepository.findById(token.projectId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found"));
            String text = """
                # Project: %s (%s)
                %s

                **Tech Stack**: %s  
                **Storage**: %s  
                **Directory**: %s  
            """.formatted(
                project.name(), project.publicId(),
                project.description() != null ? project.description() : "",
                project.techStack(), project.storagePreference(),
                project.directory() != null ? project.directory() : "N/A"
            );
            return Map.of("contents", List.of(Map.of("uri", uri, "mimeType", "text/markdown", "text", text)));
        } else if (uri.equalsIgnoreCase(archUri)) {
            List<Artifact> archs = artifactRepository.listArtifacts(token.projectId(), "ARCHITECTURE", null, null);
            if (archs.isEmpty()) {
                return Map.of("contents", List.of(Map.of("uri", uri, "mimeType", "text/markdown", "text", "# Architecture\nNo architecture currently recorded.")));
            }
            Artifact arch = archs.get(0);
            ArtifactVersion version = artifactRepository.getLatestVersion(arch.id(), token.projectId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No version found for architecture"));
            String content = artifactService.resolveContentAndVerifyHash(token.projectId(), version);
            String delimited = McpToolRegistry.formatUntrustedData(
                arch.id().toString(), version.version(), version.contentHash(), arch.sourcePlatform(), content
            );
            return Map.of("contents", List.of(Map.of("uri", uri, "mimeType", "text/markdown", "text", delimited)));
        }

        throw new ApiException(HttpStatus.NOT_FOUND, "Unknown resource URI: " + uri);
    }
}
