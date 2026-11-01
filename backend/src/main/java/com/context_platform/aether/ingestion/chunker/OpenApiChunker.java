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
public class OpenApiChunker implements Chunker {

    private static final Pattern PATH_PATTERN = Pattern.compile("^\\s{2,4}(/[a-zA-Z0-9_{}/-]+):");
    private static final Pattern METHOD_PATTERN = Pattern.compile("^\\s{4,6}(get|post|put|delete|patch|options):");

    @Override
    public boolean supports(String artifactType, String title) {
        if ("API_CONTRACT".equalsIgnoreCase(artifactType) || "API".equalsIgnoreCase(artifactType)) return true;
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.contains("openapi") || lower.contains("swagger") || lower.endsWith("api.yaml") || lower.endsWith("api.json");
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
        String baseName = title != null ? title : "OpenAPIContract";

        StringBuilder currentOp = new StringBuilder();
        String currentPath = "/";
        String currentMethod = "";
        int opStartOffset = 0;
        int opStartLine = 1;
        int currentOffset = 0;
        int chunkIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1;

            Matcher pathMatcher = PATH_PATTERN.matcher(line);
            if (pathMatcher.find()) {
                currentPath = pathMatcher.group(1);
            }

            Matcher methodMatcher = METHOD_PATTERN.matcher(line);
            if (methodMatcher.find()) {
                if (currentOp.length() > 0 && !currentOp.toString().trim().isEmpty()) {
                    String opContent = currentOp.toString().trim();
                    chunks.add(createChunk(
                        projectId, artifactId, artifactVersion, chunkIndex++,
                        baseName + " > " + currentMethod.toUpperCase() + " " + currentPath,
                        ChunkType.OPENAPI_OPERATION, opContent,
                        opStartOffset, currentOffset - 1, opStartLine, lineNum - 1,
                        baseMetadata, currentMethod, currentPath
                    ));
                    currentOp.setLength(0);
                }
                currentMethod = methodMatcher.group(1);
                opStartOffset = currentOffset;
                opStartLine = lineNum;
            }

            currentOp.append(line).append("\n");
            currentOffset += lineLen;
        }

        if (currentOp.length() > 0 && !currentOp.toString().trim().isEmpty()) {
            chunks.add(createChunk(
                projectId, artifactId, artifactVersion, chunkIndex,
                baseName + " > " + (!currentMethod.isEmpty() ? currentMethod.toUpperCase() + " " : "") + currentPath,
                ChunkType.OPENAPI_OPERATION, currentOp.toString().trim(),
                opStartOffset, Math.max(opStartOffset, currentOffset - 1),
                opStartLine, lines.length, baseMetadata, currentMethod, currentPath
            ));
        }

        return chunks;
    }

    private ArtifactChunk createChunk(
        UUID projectId, UUID artifactId, int version, int index,
        String path, ChunkType type, String content,
        int startOffset, int endOffset, int startLine, int endLine,
        Map<String, Object> baseMetadata, String method, String apiPath
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("http_method", method);
        metadata.put("api_path", apiPath);

        String hash = Sha256Utils.sha256Hex(content);
        return new ArtifactChunk(
            UUID.randomUUID(), projectId, artifactId, version, index,
            path, type, content, hash, startOffset, endOffset, startLine, endLine,
            metadata, Instant.now()
        );
    }
}
