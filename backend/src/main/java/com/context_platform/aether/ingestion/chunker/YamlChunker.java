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
public class YamlChunker implements Chunker {

    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([a-zA-Z0-9_.-]+):(?:\\s*(.*))?$");
    private static final Pattern DOC_SEPARATOR = Pattern.compile("^---\\s*$");

    @Override
    public boolean supports(String artifactType, String title) {
        if ("YAML".equalsIgnoreCase(artifactType)) return true;
        if (title == null) return false;
        String lower = title.toLowerCase();
        if (lower.contains("openapi") || lower.contains("swagger")) return false;
        if (lower.contains("docker-compose") || lower.contains("compose.yaml") || lower.contains("compose.yml")) return false;
        return lower.endsWith(".yaml") || lower.endsWith(".yml");
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
        String rootName = title != null ? title.replaceAll("\\.[^.]+$", "") : "Config";

        String[] lines = content.split("\r?\n", -1);
        StringBuilder currentSection = new StringBuilder();
        String currentSectionKey = "Header";
        int sectionStartIndex = 0;
        int sectionStartLine = 1;
        int currentOffset = 0;
        int chunkIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1;

            boolean isDocBoundary = DOC_SEPARATOR.matcher(line.trim()).matches();
            Matcher keyMatcher = TOP_LEVEL_KEY.matcher(line);

            // A new top-level section starts if line has no leading indentation and matches key: or ---
            if (!line.startsWith(" ") && !line.startsWith("\t") && !line.trim().startsWith("#") && (keyMatcher.matches() || isDocBoundary)) {
                if (currentSection.length() > 0 && !currentSection.toString().trim().isEmpty()) {
                    String secContent = currentSection.toString().trim();
                    chunks.add(new ArtifactChunk(
                        UUID.randomUUID(), projectId, artifactId, artifactVersion,
                        chunkIndex++, rootName + " > " + currentSectionKey, ChunkType.YAML_SECTION,
                        secContent, Sha256Utils.sha256Hex(secContent),
                        sectionStartIndex, currentOffset - 1,
                        sectionStartLine, lineNum - 1,
                        baseMetadata != null ? baseMetadata : Map.of(),
                        Instant.now()
                    ));
                }

                currentSection.setLength(0);
                sectionStartIndex = currentOffset;
                sectionStartLine = lineNum;

                if (isDocBoundary) {
                    currentSectionKey = "Document[" + chunkIndex + "]";
                } else {
                    currentSectionKey = keyMatcher.group(1);
                }
            }

            currentSection.append(line).append("\n");
            currentOffset += lineLen;
        }

        // Flush remaining section
        if (currentSection.length() > 0 && !currentSection.toString().trim().isEmpty()) {
            String secContent = currentSection.toString().trim();
            chunks.add(new ArtifactChunk(
                UUID.randomUUID(), projectId, artifactId, artifactVersion,
                chunkIndex++, rootName + " > " + currentSectionKey, ChunkType.YAML_SECTION,
                secContent, Sha256Utils.sha256Hex(secContent),
                sectionStartIndex, Math.max(sectionStartIndex, currentOffset - 1),
                sectionStartLine, lines.length,
                baseMetadata != null ? baseMetadata : Map.of(),
                Instant.now()
            ));
        }

        return chunks;
    }
}

