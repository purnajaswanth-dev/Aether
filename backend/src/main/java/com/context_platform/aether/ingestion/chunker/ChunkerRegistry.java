package com.context_platform.aether.ingestion.chunker;

import com.context_platform.aether.ingestion.model.ArtifactChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChunkerRegistry {

    private final List<Chunker> specificChunkers;
    private final PlainTextChunker fallbackChunker;

    public ChunkerRegistry(
        MarkdownChunker markdownChunker,
        JavaChunker javaChunker,
        TypeScriptChunker typeScriptChunker,
        SqlChunker sqlChunker,
        OpenApiChunker openApiChunker,
        DockerChunker dockerChunker,
        LogChunker logChunker,
        JsonChunker jsonChunker,
        YamlChunker yamlChunker,
        PlainTextChunker plainTextChunker
    ) {
        // Order matters: check language-specific and structured parsers before fallback
        this.specificChunkers = List.of(
            openApiChunker,
            sqlChunker,
            dockerChunker,
            jsonChunker,
            yamlChunker,
            javaChunker,
            typeScriptChunker,
            logChunker,
            markdownChunker
        );
        this.fallbackChunker = plainTextChunker;
    }

    public List<ArtifactChunk> chunk(
        UUID projectId,
        UUID artifactId,
        int artifactVersion,
        String content,
        String artifactType,
        String title,
        Map<String, Object> baseMetadata
    ) {
        for (Chunker chunker : specificChunkers) {
            if (chunker.supports(artifactType, title)) {
                List<ArtifactChunk> chunks = chunker.chunk(projectId, artifactId, artifactVersion, content, title, baseMetadata);
                if (!chunks.isEmpty()) {
                    return chunks;
                }
            }
        }
        return fallbackChunker.chunk(projectId, artifactId, artifactVersion, content, title, baseMetadata);
    }
}

