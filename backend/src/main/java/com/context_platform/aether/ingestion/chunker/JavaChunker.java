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
public class JavaChunker implements Chunker {

    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:abstract\\s+)?(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?(?:<[A-Za-z0-9_,\\s<>]+>\\s+)?([A-Za-z0-9_<>\\[\\]]+)\\s+([A-Za-z0-9_]+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[A-Za-z0-9_,\\s]+)?\\s*\\{");

    @Override
    public boolean supports(String artifactType, String title) {
        if ("SOURCE_CODE".equalsIgnoreCase(artifactType)) {
            return title == null || title.endsWith(".java") || title.contains("Java");
        }
        return title != null && title.endsWith(".java");
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

        String currentClass = title != null ? title.replace(".java", "") : "JavaClass";
        int chunkIndex = 0;

        // If simple file or no method blocks, return class-level chunk
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (classMatcher.find()) {
            currentClass = classMatcher.group(1);
        }

        // Parse methods / blocks using brace balancing
        int currentOffset = 0;
        int blockStartOffset = 0;
        int blockStartLine = 1;
        int openBraces = 0;
        boolean inBlock = false;
        String currentMemberName = currentClass;
        StringBuilder currentBlock = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            int lineLen = line.length() + 1;

            if (!inBlock) {
                Matcher methodMatcher = METHOD_PATTERN.matcher(line.trim());
                if (methodMatcher.find()) {
                    inBlock = true;
                    currentMemberName = methodMatcher.group(2);
                    blockStartOffset = currentOffset;
                    blockStartLine = lineNum;
                    openBraces = countChar(line, '{') - countChar(line, '}');
                    currentBlock.append(line).append("\n");
                    currentOffset += lineLen;
                    continue;
                }
            } else {
                currentBlock.append(line).append("\n");
                openBraces += countChar(line, '{') - countChar(line, '}');
                if (openBraces <= 0) {
                    // Method ended
                    String methodText = currentBlock.toString().trim();
                    String path = currentClass + " > " + currentMemberName + "()";
                    chunks.add(createChunk(
                        projectId, artifactId, artifactVersion, chunkIndex++,
                        path, ChunkType.JAVA_METHOD, methodText,
                        blockStartOffset, currentOffset + line.length(),
                        blockStartLine, lineNum, baseMetadata, currentClass, currentMemberName
                    ));
                    inBlock = false;
                    currentBlock.setLength(0);
                    openBraces = 0;
                }
                currentOffset += lineLen;
                continue;
            }

            currentOffset += lineLen;
        }

        // If no method blocks were detected, return full class chunk
        if (chunks.isEmpty()) {
            chunks.add(createChunk(
                projectId, artifactId, artifactVersion, 0,
                currentClass, ChunkType.JAVA_CLASS, content.trim(),
                0, content.length(), 1, lines.length, baseMetadata, currentClass, currentClass
            ));
        }

        return chunks;
    }

    private ArtifactChunk createChunk(
        UUID projectId, UUID artifactId, int version, int index,
        String path, ChunkType type, String content,
        int startOffset, int endOffset, int startLine, int endLine,
        Map<String, Object> baseMetadata, String className, String memberName
    ) {
        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put("class_name", className);
        metadata.put("member_name", memberName);
        metadata.put("language", "java");

        String hash = Sha256Utils.sha256Hex(content);
        return new ArtifactChunk(
            UUID.randomUUID(), projectId, artifactId, version, index,
            path, type, content, hash, startOffset, endOffset, startLine, endLine,
            metadata, Instant.now()
        );
    }

    private static int countChar(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) count++;
        }
        return count;
    }
}
