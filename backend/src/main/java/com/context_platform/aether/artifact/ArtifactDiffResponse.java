package com.context_platform.aether.artifact;

import java.util.List;
import java.util.UUID;

public record ArtifactDiffResponse(
    UUID artifactId,
    int v1,
    int v2,
    String v1Hash,
    String v2Hash,
    int insertions,
    int deletions,
    int modifications,
    List<DiffLine> lines
) {
    public record DiffLine(
        String type, // EQUAL, INSERT, DELETE, CHANGE
        Integer oldLineNumber,
        Integer newLineNumber,
        String content
    ) {}
}

