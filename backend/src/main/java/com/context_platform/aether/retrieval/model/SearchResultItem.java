package com.context_platform.aether.retrieval.model;

import com.context_platform.aether.ingestion.model.ChunkType;

import java.util.UUID;

public record SearchResultItem(
    UUID chunkId,
    UUID artifactId,
    int artifactVersion,
    String title,
    String parentSectionPath,
    ChunkType chunkType,
    String content,
    String contentHash,
    int startOffset,
    int endOffset,
    int startLine,
    int endLine,
    String approvalState,
    boolean isSuperseded,
    double rrfScore,
    String sourceMethods,
    String untrustedFormattedContent
) {}

