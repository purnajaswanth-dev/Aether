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
public class SqlChunker implements Chunker {

    private static final Pattern STATEMENT_TYPE_PATTERN = Pattern.compile("(?i)^(CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:TABLE|INDEX|VIEW|TRIGGER|FUNCTION|PROCEDURE)|ALTER\\s+TABLE|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+([A-Za-z0-9_.\"]+)");

    @Override
    public boolean supports(String artifactType, String title) {
        if ("DATABASE".equalsIgnoreCase(artifactType) || "DATABASE_DESIGN".equalsIgnoreCase(artifactType)) return true;
        return title != null && (title.toLowerCase().endsWith(".sql") || title.toLowerCase().contains("migration"));
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
        String baseName = title != null ? title : "DatabaseSchema";

        StringBuilder currentStmt = new StringBuilder();
        int stmtStartOffset = 0;
        int stmtStartLine = 1;
        int currentOffset = 0;
        int chunkIndex = 0;
        String currentTarget = "Statement";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1;

            if (currentStmt.length() == 0) {
                stmtStartOffset = currentOffset;
                stmtStartLine = lineNum;
                Matcher m = STATEMENT_TYPE_PATTERN.matcher(line.trim());
                if (m.find()) {
                    currentTarget = m.group(1).toUpperCase() + " " + m.group(2);
                } else if (!line.trim().startsWith("--") && !line.trim().isEmpty()) {
                    currentTarget = line.trim().substring(0, Math.min(30, line.trim().length()));
                }
            }

            currentStmt.append(line).append("\n");

            // Check if statement ends with ';' outside single quotes
            if (line.trim().endsWith(";") && !line.trim().startsWith("--")) {
                String stmtContent = currentStmt.toString().trim();
                if (!stmtContent.isEmpty()) {
                    chunks.add(createChunk(
                        projectId, artifactId, artifactVersion, chunkIndex++,
                        baseName + " > " + currentTarget, ChunkType.SQL_STATEMENT,
                        stmtContent, stmtStartOffset, currentOffset + line.length(),
                        stmtStartLine, lineNum, baseMetadata, currentTarget
                    ));
                    currentStmt.setLength(0);
                    currentTarget = "Statement";
                }
            }

            currentOffset += lineLen;
        }

        // Flush remaining statement
        if (currentStmt.length() > 0 && !currentStmt.toString().trim().isEmpty()) {
            chunks.add(createChunk(
                projectId, artifactId, artifactVersion, chunkIndex,
                baseName + " > " + currentTarget, ChunkType.SQL_STATEMENT,
                currentStmt.toString().trim(), stmtStartOffset, Math.max(stmtStartOffset, currentOffset - 1),
                stmtStartLine, lines.length, baseMetadata, currentTarget
            ));
        }

        return chunks;
    }

    private ArtifactChunk createChunk(
        UUID projectId, UUID artifactId, int version, int index,
        String path, ChunkType type, String content,
        int startOffset, int endOffset, int startLine, int endLine,
        Map<String, Object> baseMetadata, String target
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("sql_target", target);
        metadata.put("language", "sql");

        String hash = Sha256Utils.sha256Hex(content);
        return new ArtifactChunk(
            UUID.randomUUID(), projectId, artifactId, version, index,
            path, type, content, hash, startOffset, endOffset, startLine, endLine,
            metadata, Instant.now()
        );
    }
}
