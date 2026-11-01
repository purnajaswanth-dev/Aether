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
public class LogChunker implements Chunker {

    // Matches standard ISO timestamps e.g. 2026-10-31T17:55:00 or 2026-10-31 17:55:00
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}");

    @Override
    public boolean supports(String artifactType, String title) {
        if ("LOG".equalsIgnoreCase(artifactType) || "LOGS".equalsIgnoreCase(artifactType)) return true;
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.endsWith(".log") || lower.contains("log") || lower.contains("trace");
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
        String baseName = title != null ? title : "ExecutionLogs";

        StringBuilder currentLog = new StringBuilder();
        String currentHeader = "LogEntry";
        int logStartOffset = 0;
        int logStartLine = 1;
        int currentOffset = 0;
        int chunkIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1;

            Matcher matcher = TIMESTAMP_PATTERN.matcher(line.trim());
            // If new timestamp line and accumulated at least 5 lines or an error entry, chunk it
            boolean isNewTimestamp = matcher.find();
            if (isNewTimestamp && currentLog.length() > 0 && (currentLog.toString().lines().count() >= 10 || currentLog.toString().contains("ERROR") || currentLog.toString().contains("Exception"))) {
                chunks.add(createChunk(
                    projectId, artifactId, artifactVersion, chunkIndex++,
                    baseName + " > " + currentHeader, ChunkType.LOG_ENTRY,
                    currentLog.toString().trim(), logStartOffset, currentOffset - 1,
                    logStartLine, lineNum - 1, baseMetadata, currentHeader
                ));
                currentLog.setLength(0);
                logStartOffset = currentOffset;
                logStartLine = lineNum;
                currentHeader = line.trim().substring(0, Math.min(40, line.trim().length()));
            } else if (isNewTimestamp && currentLog.length() == 0) {
                currentHeader = line.trim().substring(0, Math.min(40, line.trim().length()));
                logStartOffset = currentOffset;
                logStartLine = lineNum;
            }

            currentLog.append(line).append("\n");
            currentOffset += lineLen;
        }

        if (currentLog.length() > 0 && !currentLog.toString().trim().isEmpty()) {
            chunks.add(createChunk(
                projectId, artifactId, artifactVersion, chunkIndex,
                baseName + " > " + currentHeader, ChunkType.LOG_ENTRY,
                currentLog.toString().trim(), logStartOffset, Math.max(logStartOffset, currentOffset - 1),
                logStartLine, lines.length, baseMetadata, currentHeader
            ));
        }

        return chunks;
    }

    private ArtifactChunk createChunk(
        UUID projectId, UUID artifactId, int version, int index,
        String path, ChunkType type, String content,
        int startOffset, int endOffset, int startLine, int endLine,
        Map<String, Object> baseMetadata, String header
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("log_header", header);
        boolean hasError = content.contains("ERROR") || content.contains("Exception") || content.contains("FATAL");
        metadata.put("has_error", hasError);

        String hash = Sha256Utils.sha256Hex(content);
        return new ArtifactChunk(
            UUID.randomUUID(), projectId, artifactId, version, index,
            path, type, content, hash, startOffset, endOffset, startLine, endLine,
            metadata, Instant.now()
        );
    }
}
