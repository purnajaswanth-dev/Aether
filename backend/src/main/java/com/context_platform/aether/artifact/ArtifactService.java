package com.context_platform.aether.artifact;

import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.ResourceNotFoundException;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.security.ProjectSecurityChecker;
import com.context_platform.aether.storage.StorageResult;
import com.context_platform.aether.storage.StorageService;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.ChangeDelta;
import com.github.difflib.patch.DeleteDelta;
import com.github.difflib.patch.InsertDelta;
import com.github.difflib.patch.Patch;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ArtifactService {

    private static final int INLINE_BYTE_LIMIT = 256 * 1024; // 256 KB

    private final ArtifactRepository artifactRepository;
    private final StorageService storageService;
    private final TimelineService timelineService;
    private final ProjectSecurityChecker securityChecker;

    public ArtifactService(
        ArtifactRepository artifactRepository,
        StorageService storageService,
        TimelineService timelineService,
        ProjectSecurityChecker securityChecker
    ) {
        this.artifactRepository = artifactRepository;
        this.storageService = storageService;
        this.timelineService = timelineService;
        this.securityChecker = securityChecker;
    }

    @Transactional
    public ArtifactResponse createArtifact(UUID projectId, CreateArtifactRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();
        return createArtifactInternal(projectId, request, actor);
    }

    @Transactional
    public ArtifactResponse createArtifact(UUID projectId, CreateArtifactRequest request, ActorContext actor) {
        return createArtifactInternal(projectId, request, actor);
    }

    private ArtifactResponse createArtifactInternal(UUID projectId, CreateArtifactRequest request, ActorContext actor) {
        byte[] contentBytes = request.content().getBytes(StandardCharsets.UTF_8);
        String hash = storageService.computeSha256(contentBytes);
        long byteSize = contentBytes.length;

        // Persist to disk via StorageService for filesystem storage & integrity
        StorageResult storageResult = storageService.store(projectId, request.title(), contentBytes);

        String inlineContent = (byteSize <= INLINE_BYTE_LIMIT) ? request.content() : null;
        String storageKey = storageResult.storageKey();

        Artifact artifact = artifactRepository.createArtifact(
            projectId,
            request.title(),
            request.sourcePlatform(),
            request.sourceReference(),
            request.artifactType(),
            null
        );

        ArtifactVersion version = artifactRepository.appendVersion(
            artifact.id(),
            projectId,
            1,
            inlineContent,
            storageKey,
            hash,
            byteSize,
            actor.actorId(),
            actor.actorType().name(),
            request.changeSummary() != null ? request.changeSummary() : "Initial version"
        );

        // Timeline event
        timelineService.append(
            projectId,
            null,
            request.sourcePlatform(),
            "ARTIFACT_SAVED",
            artifact.id(),
            null,
            1,
            "SUCCESS",
            actor.actorId(),
            actor.actorType().name(),
            "Saved artifact '" + artifact.title() + "' (v1, " + byteSize + " bytes, hash " + hash.substring(0, 8) + "…)",
            "{\"artifactId\":\"" + artifact.id() + "\",\"version\":1,\"hash\":\"" + hash + "\"}"
        );

        return new ArtifactResponse(
            artifact.id(),
            projectId,
            artifact.title(),
            artifact.sourcePlatform(),
            artifact.sourceReference(),
            artifact.artifactType(),
            artifact.relatedTaskId(),
            1,
            hash,
            byteSize,
            request.content(),
            artifact.createdAt()
        );
    }

    @Transactional
    public ArtifactVersionResponse createVersion(UUID projectId, UUID artifactId, CreateVersionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();
        return createVersionInternal(projectId, artifactId, request, actor);
    }

    @Transactional
    public ArtifactVersionResponse createVersion(UUID projectId, UUID artifactId, CreateVersionRequest request, ActorContext actor) {
        return createVersionInternal(projectId, artifactId, request, actor);
    }

    private ArtifactVersionResponse createVersionInternal(UUID projectId, UUID artifactId, CreateVersionRequest request, ActorContext actor) {
        Artifact artifact = artifactRepository.findArtifactById(artifactId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Artifact", artifactId));

        ArtifactVersion latest = artifactRepository.getLatestVersion(artifactId, projectId)
            .orElseThrow(() -> new ApiException("Artifact has no existing versions", HttpStatus.INTERNAL_SERVER_ERROR));

        int nextVersion = latest.version() + 1;
        byte[] contentBytes = request.content().getBytes(StandardCharsets.UTF_8);
        String hash = storageService.computeSha256(contentBytes);
        long byteSize = contentBytes.length;

        StorageResult storageResult = storageService.store(projectId, artifact.title() + "_v" + nextVersion, contentBytes);
        String inlineContent = (byteSize <= INLINE_BYTE_LIMIT) ? request.content() : null;

        ArtifactVersion version = artifactRepository.appendVersion(
            artifactId,
            projectId,
            nextVersion,
            inlineContent,
            storageResult.storageKey(),
            hash,
            byteSize,
            actor.actorId(),
            actor.actorType().name(),
            request.changeSummary() != null ? request.changeSummary() : "Version " + nextVersion
        );

        timelineService.append(
            projectId,
            null,
            artifact.sourcePlatform(),
            "ARTIFACT_VERSION_CREATED",
            artifactId,
            null,
            nextVersion,
            "SUCCESS",
            actor.actorId(),
            actor.actorType().name(),
            "Created version " + nextVersion + " for artifact '" + artifact.title() + "' (hash " + hash.substring(0, 8) + "…)",
            "{\"artifactId\":\"" + artifactId + "\",\"version\":" + nextVersion + ",\"hash\":\"" + hash + "\"}"
        );

        return new ArtifactVersionResponse(
            version.id(),
            artifactId,
            nextVersion,
            hash,
            byteSize,
            actor.actorId(),
            actor.actorType().name(),
            version.changeSummary(),
            request.content(),
            version.createdAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ArtifactResponse> listArtifacts(UUID projectId, String type, String platform, String search, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        List<Artifact> artifacts = artifactRepository.listArtifacts(projectId, type, platform, search);
        List<ArtifactResponse> result = new ArrayList<>();

        for (Artifact a : artifacts) {
            ArtifactVersion latest = artifactRepository.getLatestVersion(a.id(), projectId).orElse(null);
            result.add(new ArtifactResponse(
                a.id(),
                a.projectId(),
                a.title(),
                a.sourcePlatform(),
                a.sourceReference(),
                a.artifactType(),
                a.relatedTaskId(),
                latest != null ? latest.version() : 1,
                latest != null ? latest.contentHash() : "",
                latest != null ? latest.byteSize() : 0,
                null,
                a.createdAt()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ArtifactResponse getLatest(UUID projectId, UUID artifactId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        Artifact artifact = artifactRepository.findArtifactById(artifactId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Artifact", artifactId));

        ArtifactVersion latest = artifactRepository.getLatestVersion(artifactId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("ArtifactVersion", artifactId));

        String content = resolveContentAndVerifyHash(projectId, latest);

        return new ArtifactResponse(
            artifact.id(),
            projectId,
            artifact.title(),
            artifact.sourcePlatform(),
            artifact.sourceReference(),
            artifact.artifactType(),
            artifact.relatedTaskId(),
            latest.version(),
            latest.contentHash(),
            latest.byteSize(),
            content,
            artifact.createdAt()
        );
    }

    @Transactional(readOnly = true)
    public ArtifactVersionResponse getVersion(UUID projectId, UUID artifactId, int versionNum, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        ArtifactVersion version = artifactRepository.getVersion(artifactId, versionNum, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("ArtifactVersion " + versionNum + " for Artifact", artifactId));

        String content = resolveContentAndVerifyHash(projectId, version);

        return new ArtifactVersionResponse(
            version.id(),
            artifactId,
            version.version(),
            version.contentHash(),
            version.byteSize(),
            version.createdBy(),
            version.actorType(),
            version.changeSummary(),
            content,
            version.createdAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ArtifactVersionResponse> listVersions(UUID projectId, UUID artifactId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        List<ArtifactVersion> versions = artifactRepository.listVersions(artifactId, projectId);
        return versions.stream().map(v -> new ArtifactVersionResponse(
            v.id(),
            v.artifactId(),
            v.version(),
            v.contentHash(),
            v.byteSize(),
            v.createdBy(),
            v.actorType(),
            v.changeSummary(),
            null,
            v.createdAt()
        )).toList();
    }

    @Transactional(readOnly = true)
    public ArtifactDiffResponse compareVersions(UUID projectId, UUID artifactId, int v1Num, int v2Num, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        ArtifactVersion v1 = artifactRepository.getVersion(artifactId, v1Num, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("ArtifactVersion " + v1Num, artifactId));
        ArtifactVersion v2 = artifactRepository.getVersion(artifactId, v2Num, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("ArtifactVersion " + v2Num, artifactId));

        String c1 = resolveContentAndVerifyHash(projectId, v1);
        String c2 = resolveContentAndVerifyHash(projectId, v2);

        List<String> lines1 = Arrays.asList(c1.split("\r?\n", -1));
        List<String> lines2 = Arrays.asList(c2.split("\r?\n", -1));

        Patch<String> patch = DiffUtils.diff(lines1, lines2);

        List<ArtifactDiffResponse.DiffLine> diffLines = new ArrayList<>();
        int insertions = 0;
        int deletions = 0;
        int modifications = 0;

        int idx1 = 0;
        int idx2 = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            // Lines before this delta were equal
            int targetSourcePos = delta.getSource().getPosition();
            while (idx1 < targetSourcePos && idx1 < lines1.size()) {
                diffLines.add(new ArtifactDiffResponse.DiffLine("EQUAL", idx1 + 1, idx2 + 1, lines1.get(idx1)));
                idx1++;
                idx2++;
            }

            if (delta instanceof InsertDelta<String> insert) {
                for (String line : insert.getTarget().getLines()) {
                    diffLines.add(new ArtifactDiffResponse.DiffLine("INSERT", null, idx2 + 1, line));
                    idx2++;
                    insertions++;
                }
            } else if (delta instanceof DeleteDelta<String> delete) {
                for (String line : delete.getSource().getLines()) {
                    diffLines.add(new ArtifactDiffResponse.DiffLine("DELETE", idx1 + 1, null, line));
                    idx1++;
                    deletions++;
                }
            } else if (delta instanceof ChangeDelta<String> change) {
                for (String line : change.getSource().getLines()) {
                    diffLines.add(new ArtifactDiffResponse.DiffLine("DELETE", idx1 + 1, null, line));
                    idx1++;
                    deletions++;
                }
                for (String line : change.getTarget().getLines()) {
                    diffLines.add(new ArtifactDiffResponse.DiffLine("INSERT", null, idx2 + 1, line));
                    idx2++;
                    insertions++;
                }
                modifications++;
            }
        }

        // Remaining equal lines
        while (idx1 < lines1.size() && idx2 < lines2.size()) {
            diffLines.add(new ArtifactDiffResponse.DiffLine("EQUAL", idx1 + 1, idx2 + 1, lines1.get(idx1)));
            idx1++;
            idx2++;
        }

        return new ArtifactDiffResponse(
            artifactId,
            v1Num,
            v2Num,
            v1.contentHash(),
            v2.contentHash(),
            insertions,
            deletions,
            modifications,
            diffLines
        );
    }

    public String resolveContentAndVerifyHash(UUID projectId, ArtifactVersion version) {
        String content;
        if (version.content() != null && !version.content().isBlank()) {
            content = version.content();
            // Verify hash on read
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            String computed = storageService.computeSha256(bytes);
            if (!computed.equalsIgnoreCase(version.contentHash())) {
                throw new ApiException("Integrity check failed: inline content hash does not match recorded SHA-256 hash", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else if (version.storageKey() != null) {
            byte[] retrieved = storageService.retrieve(projectId, version.storageKey(), version.contentHash());
            content = new String(retrieved, StandardCharsets.UTF_8);
        } else {
            content = "";
        }
        return content;
    }
}

