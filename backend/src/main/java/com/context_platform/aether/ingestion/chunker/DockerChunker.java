package com.context_platform.aether.ingestion.chunker;

import com.context_platform.aether.common.Sha256Utils;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.ChunkType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DockerChunker implements Chunker {

    private static final Pattern FROM_PATTERN = Pattern.compile("(?i)^FROM\\s+\\S+(?:\\s+AS\\s+([A-Za-z0-9_-]+))?");
    private static final Pattern COMPOSE_SERVICE_PATTERN = Pattern.compile("^\\s{2}([A-Za-z0-9_-]+):");

    @Override
    public boolean supports(String artifactType, String title) {
        if ("DEPLOYMENT".equalsIgnoreCase(artifactType) || "INFRASTRUCTURE".equalsIgnoreCase(artifactType)) return true;
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.contains("dockerfile") || lower.contains("docker-compose") || lower.endsWith(".dockerfile");
    }

    @Override
    public List<ArtifactChunk> chunk(
        UUID projectId,
        UUID artifactId,
        int artifactVersion,
        String content,
        String title,
        Map<String, Object> baseMetadata
    ) {
        if (content == null || content.isBlank()) return List.of();

        List<ArtifactChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\r?\n", -1);
        String baseName = title != null ? title : "DockerConfig";

        StringBuilder currentStage = new StringBuilder();
        String currentStageName = "base";
        int stageStartOffset = 0;
        int stageStartLine = 1;
        int currentOffset = 0;
        int chunkIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1;

            Matcher fromMatcher = FROM_PATTERN.matcher(line.trim());
            Matcher composeMatcher = COMPOSE_SERVICE_PATTERN.matcher(line);

            boolean isNewStage = fromMatcher.find() || composeMatcher.find();
            if (isNewStage && currentStage.length() > 0 && !currentStage.toString().trim().isEmpty()) {
                chunks.add(createChunk(
                    projectId, artifactId, artifactVersion, chunkIndex++,
                    baseName + " > " + currentStageName, ChunkType.DOCKER_STAGE,
                    currentStage.toString().trim(), stageStartOffset, currentOffset - 1,
                    stageStartLine, lineNum - 1, baseMetadata, currentStageName
                ));
                currentStage.setLength(0);
                stageStartOffset = currentOffset;
                stageStartLine = lineNum;
            }

            if (fromMatcher.matches()) {
                currentStageName = fromMatcher.group(1) != null ? fromMatcher.group(1) : "stage-" + chunkIndex;
            } else if (composeMatcher.matches()) {
                currentStageName = composeMatcher.group(1);
            }

            currentStage.append(line).append("\n");
            currentOffset += lineLen;
        }

        if (currentStage.length() > 0 && !currentStage.toString().trim().isEmpty()) {
            chunks.add(createChunk(
                projectId, artifactId, artifactVersion, chunkIndex,
                baseName + " > " + currentStageName, ChunkType.DOCKER_STAGE,
                currentStage.toString().trim(), stageStartOffset, Math.max(stageStartOffset, currentOffset - 1),
                stageStartLine, lines.length, baseMetadata, currentStageName
            ));
        }

        return chunks;
    }

    private ArtifactChunk createChunk(
        UUID projectId, UUID artifactId, int version, int index,
        String path, ChunkType type, String content,
        int startOffset, int endOffset, int startLine, int endLine,
        Map<String, Object> baseMetadata, String stageName
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("docker_stage", stageName);

        String hash = Sha256Utils.sha256Hex(content);
        return new ArtifactChunk(
            UUID.randomUUID(), projectId, artifactId, version, index,
            path, type, content, hash, startOffset, endOffset, startLine, endLine,
            metadata, Instant.now()
        );
    }
}
