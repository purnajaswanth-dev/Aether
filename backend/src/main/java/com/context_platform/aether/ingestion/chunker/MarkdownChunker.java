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
public class MarkdownChunker implements Chunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    @Override
    public boolean supports(String artifactType, String title) {
        if ("ARCHITECTURE".equalsIgnoreCase(artifactType) || "SPECIFICATION".equalsIgnoreCase(artifactType)) return true;
        if (title == null) return false;
        String lower = title.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.contains("spec") || lower.contains("architecture");
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
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<ArtifactChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\r?\n", -1);

        List<HeadingLevel> headingStack = new ArrayList<>();
        StringBuilder currentSection = new StringBuilder();
        int sectionStartIndex = 0;
        int sectionStartLine = 1;
        int currentOffset = 0;
        int chunkIndex = 0;
        boolean inCodeBlock = false;

        String currentPath = title != null ? title : "Root";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1; // +1 for newline

            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line.trim());
            if (!inCodeBlock && headingMatcher.matches()) {
                // If we have accumulated text, flush previous chunk
                if (currentSection.length() > 0 && !currentSection.toString().trim().isEmpty()) {
                    String sectionContent = currentSection.toString().trim();
                    chunks.add(createChunk(
                        projectId, artifactId, artifactVersion, chunkIndex++,
                        currentPath, ChunkType.MARKDOWN_SECTION,
                        sectionContent, sectionStartIndex, currentOffset - 1,
                        sectionStartLine, lineNum - 1, baseMetadata
                    ));
                    currentSection.setLength(0);
                }

                int level = headingMatcher.group(1).length();
                String headingText = headingMatcher.group(2).trim();

                // Adjust hierarchy stack
                while (!headingStack.isEmpty() && headingStack.get(headingStack.size() - 1).level() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(new HeadingLevel(level, headingText));

                // Build path: e.g. "Architecture > Database > PostgreSQL"
                StringBuilder pathBuilder = new StringBuilder(title != null ? title : "Root");
                for (HeadingLevel hl : headingStack) {
                    pathBuilder.append(" > ").append(hl.text());
                }
                currentPath = pathBuilder.toString();

                sectionStartIndex = currentOffset;
                sectionStartLine = lineNum;
            }

            currentSection.append(line).append("\n");
            currentOffset += lineLen;
        }

        // Flush final section
        if (currentSection.length() > 0 && !currentSection.toString().trim().isEmpty()) {
            String sectionContent = currentSection.toString().trim();
            chunks.add(createChunk(
                projectId, artifactId, artifactVersion, chunkIndex,
                currentPath, ChunkType.MARKDOWN_SECTION,
                sectionContent, sectionStartIndex, Math.max(sectionStartIndex, currentOffset - 1),
                sectionStartLine, lines.length, baseMetadata
            ));
        }

        return chunks;
    }

    private ArtifactChunk createChunk(
        UUID projectId, UUID artifactId, int version, int index,
        String path, ChunkType type, String content,
        int startOffset, int endOffset, int startLine, int endLine,
        Map<String, Object> baseMetadata
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("heading_path", path);
        metadata.put("total_lines", endLine - startLine + 1);

        String hash = Sha256Utils.sha256Hex(content);
        return new ArtifactChunk(
            UUID.randomUUID(), projectId, artifactId, version, index,
            path, type, content, hash, startOffset, endOffset, startLine, endLine,
            metadata, Instant.now()
        );
    }

    private record HeadingLevel(int level, String text) {}
}
