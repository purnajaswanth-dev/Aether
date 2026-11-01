package com.context_platform.aether.ingestion.chunker;

import com.context_platform.aether.ingestion.model.ArtifactChunk;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface Chunker {
    boolean supports(String artifactType, String title);
    List<ArtifactChunk> chunk(
        UUID projectId,
        UUID artifactId,
        int artifactVersion,
        String content,
        String title,
        Map<String, Object> baseMetadata
    );
}

