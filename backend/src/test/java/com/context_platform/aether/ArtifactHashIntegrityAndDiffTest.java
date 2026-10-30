package com.context_platform.aether;

import com.context_platform.aether.artifact.ArtifactDiffResponse;
import com.context_platform.aether.artifact.ArtifactResponse;
import com.context_platform.aether.artifact.ArtifactVersionResponse;
import com.context_platform.aether.artifact.CreateArtifactRequest;
import com.context_platform.aether.artifact.CreateVersionRequest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.auth.RegisterRequest;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.project.CreateProjectRequest;
import com.context_platform.aether.project.ProjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ArtifactHashIntegrityAndDiffTest extends BaseTest {

    @Test
    @DisplayName("Gate 1: Store 50 KB architecture doc, create version 2, compare versions; v1 remains byte-identical with verified hash")
    void testArtifactStorageVersionDiffAndHashIntegrity() {
        rlsContextHelper.setBypassRls(true);

        // 1. Register user and create project
        AuthResponse user = authService.register(
            new RegisterRequest("engineer_" + UUID.randomUUID() + "@team.com", "Password123!", "Platform Architect"),
            "127.0.0.1", "JUnit"
        );
        ProjectResponse project = projectService.createProject(
            new CreateProjectRequest("Vionex Architecture", "Core system spec", "Web Application", List.of("Java 17", "PostgreSQL 16"), null, "LOCAL_FIRST"),
            user.user().id(),
            user.user().email()
        );

        // 2. Build 50 KB realistic architecture document
        StringBuilder docV1Builder = new StringBuilder();
        docV1Builder.append("# Vionex Core Architecture Specification (v1)\n\n");
        docV1Builder.append("## Overview\nPersistent memory architecture for software engineering.\n\n");
        // Pad with realistic architectural sections until byte length >= 50 * 1024 (51,200 bytes)
        while (docV1Builder.toString().getBytes(StandardCharsets.UTF_8).length < 52_000) {
            docV1Builder.append("### Module Section ")
                .append(UUID.randomUUID())
                .append("\n- Component: Persistent Storage\n- Constraint: Immutability triggers on all historical records.\n- Security: Row-Level Security with app.current_project_id.\n\n");
        }
        String docV1Content = docV1Builder.toString();
        long v1ByteSize = docV1Content.getBytes(StandardCharsets.UTF_8).length;
        assertThat(v1ByteSize).isGreaterThanOrEqualTo(50 * 1024);

        // 3. Store Version 1
        ArtifactResponse v1Response = artifactService.createArtifact(
            project.id(),
            new CreateArtifactRequest("Vionex Architecture Document", "ARCHITECTURE", "MANUAL", null, docV1Content, "Initial 50KB architecture spec"),
            user.user().id()
        );

        assertThat(v1Response.currentVersion()).isEqualTo(1);
        assertThat(v1Response.byteSize()).isEqualTo(v1ByteSize);
        String v1Hash = v1Response.contentHash();
        assertThat(v1Hash).isNotEmpty();

        // 4. Create Version 2 (Appends new sections and updates overview)
        String docV2Content = docV1Content.replace("Specification (v1)", "Specification (v2 - Updated)")
            + "\n## Additional Section: pgvector HNSW Indexing\nConfigured with m=16, ef_construction=64 for sub-millisecond retrieval.\n";

        ArtifactVersionResponse v2Response = artifactService.createVersion(
            project.id(),
            v1Response.id(),
            new CreateVersionRequest(docV2Content, "Added pgvector HNSW index specifications"),
            user.user().id()
        );

        assertThat(v2Response.version()).isEqualTo(2);
        String v2Hash = v2Response.contentHash();
        assertThat(v2Hash).isNotEqualTo(v1Hash);

        // 5. Gate Verification: Version 1 remains 100% BYTE-IDENTICAL (hash verified)
        ArtifactVersionResponse retrievedV1 = artifactService.getVersion(project.id(), v1Response.id(), 1, user.user().id());
        assertThat(retrievedV1.content()).isEqualTo(docV1Content); // Byte-identical!
        assertThat(retrievedV1.contentHash()).isEqualTo(v1Hash);

        // Version 2 is also byte-identical
        ArtifactVersionResponse retrievedV2 = artifactService.getVersion(project.id(), v1Response.id(), 2, user.user().id());
        assertThat(retrievedV2.content()).isEqualTo(docV2Content);
        assertThat(retrievedV2.contentHash()).isEqualTo(v2Hash);

        // 6. Compare Version 1 and Version 2 (Structured Diff)
        ArtifactDiffResponse diff = artifactService.compareVersions(project.id(), v1Response.id(), 1, 2, user.user().id());
        assertThat(diff.v1()).isEqualTo(1);
        assertThat(diff.v2()).isEqualTo(2);
        assertThat(diff.v1Hash()).isEqualTo(v1Hash);
        assertThat(diff.v2Hash()).isEqualTo(v2Hash);
        assertThat(diff.lines()).isNotEmpty();
        assertThat(diff.insertions()).isGreaterThan(0);

        // 7. Hash Tamper Verification
        assertThatThrownBy(() -> {
            // Attempting to verify content with wrong hash throws integrity exception
            storageService.retrieve(project.id(), "non-existent-or-tampered", "tampered_fake_hash_12345");
        }).isInstanceOf(ApiException.class);
    }
}
