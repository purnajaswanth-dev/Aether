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
public class TypeScriptChunker implements Chunker {

    private static final Pattern DECL_PATTERN = Pattern.compile("^(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?(?:function|class|interface|type|const|enum)\\s+([A-Za-z0-9_]+)");

    @Override
    public boolean supports(String artifactType, String title) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".js") || lower.endsWith(".jsx");
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
        String baseName = title != null ? title : "TypeScriptModule";

        StringBuilder currentDecl = new StringBuilder();
        String currentDeclName = baseName;
        int declStartOffset = 0;
        int declStartLine = 1;
        int currentOffset = 0;
        int chunkIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1;

            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                Matcher matcher = DECL_PATTERN.matcher(line.trim());
                if (matcher.find()) {
                    if (currentDecl.length() > 0 && !currentDecl.toString().trim().isEmpty()) {
                        String declContent = currentDecl.toString().trim();
                        chunks.add(createChunk(
                            projectId, artifactId, artifactVersion, chunkIndex++,
                            baseName + " > " + currentDeclName, ChunkType.TYPESCRIPT_DECL,
                            declContent, declStartOffset, currentOffset - 1,
                            declStartLine, lineNum - 1, baseMetadata, currentDeclName
                        ));
                        currentDecl.setLength(0);
                    }
                    currentDeclName = matcher.group(1);
                    declStartOffset = currentOffset;
                    declStartLine = lineNum;
                }
            }

            currentDecl.append(line).append("\n");
            currentOffset += lineLen;
        }

        if (currentDecl.length() > 0 && !currentDecl.toString().trim().isEmpty()) {
            chunks.add(createChunk(
                projectId, artifactId, artifactVersion, chunkIndex,
                baseName + " > " + currentDeclName, ChunkType.TYPESCRIPT_DECL,
                currentDecl.toString().trim(), declStartOffset, Math.max(declStartOffset, currentOffset - 1),
                declStartLine, lines.length, baseMetadata, currentDeclName
            ));
        }

        return chunks;
    }

    private ArtifactChunk createChunk(
        UUID projectId, UUID artifactId, int version, int index,
        String path, ChunkType type, String content,
        int startOffset, int endOffset, int startLine, int endLine,
        Map<String, Object> baseMetadata, String declName
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("declaration_name", declName);
        metadata.put("language", "typescript");

        String hash = Sha256Utils.sha256Hex(content);
        return new ArtifactChunk(
            UUID.randomUUID(), projectId, artifactId, version, index,
            path, type, content, hash, startOffset, endOffset, startLine, endLine,
            metadata, Instant.now()
        );
    }
}
