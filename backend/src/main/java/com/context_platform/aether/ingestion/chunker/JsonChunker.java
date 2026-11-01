package com.context_platform.aether.ingestion.chunker;

import com.context_platform.aether.common.Sha256Utils;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.ChunkType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class JsonChunker implements Chunker {

    private final ObjectMapper objectMapper;

    public JsonChunker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String artifactType, String title) {
        if ("JSON".equalsIgnoreCase(artifactType)) return true;
        if (title == null) return false;
        String lower = title.toLowerCase();
        if (lower.contains("openapi") || lower.contains("swagger")) return false;
        return lower.endsWith(".json");
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
        String rootName = title != null ? title.replaceAll("\\.[^.]+$", "") : "Document";

        try {
            JsonNode rootNode = objectMapper.readTree(content);
            String[] lines = content.split("\r?\n", -1);

            if (rootNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = rootNode.fields();
                int chunkIndex = 0;

                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String key = field.getKey();
                    JsonNode value = field.getValue();

                    String chunkContent = "\"" + key + "\": " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
                    String sectionPath = rootName + " > " + key;

                    int startOffset = content.indexOf("\"" + key + "\"");
                    if (startOffset == -1) startOffset = 0;

                    int startLine = 1;
                    int cur = 0;
                    for (int l = 0; l < lines.length; l++) {
                        if (cur + lines[l].length() >= startOffset) {
                            startLine = l + 1;
                            break;
                        }
                        cur += lines[l].length() + 1;
                    }

                    int endOffset = Math.min(content.length(), startOffset + chunkContent.length());
                    int endLine = Math.min(lines.length, startLine + chunkContent.split("\r?\n").length - 1);

                    chunks.add(new ArtifactChunk(
                        UUID.randomUUID(), projectId, artifactId, artifactVersion,
                        chunkIndex++, sectionPath, ChunkType.JSON_BLOCK,
                        chunkContent, Sha256Utils.sha256Hex(chunkContent),
                        startOffset, endOffset, startLine, endLine,
                        baseMetadata != null ? baseMetadata : Map.of(),
                        Instant.now()
                    ));
                }
            } else if (rootNode.isArray()) {
                int chunkIndex = 0;
                for (int i = 0; i < rootNode.size(); i++) {
                    JsonNode elem = rootNode.get(i);
                    String chunkContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(elem);
                    String sectionPath = rootName + "[" + i + "]";

                    int startOffset = content.indexOf(chunkContent.substring(0, Math.min(20, chunkContent.length())));
                    if (startOffset == -1) startOffset = 0;
                    int endOffset = Math.min(content.length(), startOffset + chunkContent.length());

                    chunks.add(new ArtifactChunk(
                        UUID.randomUUID(), projectId, artifactId, artifactVersion,
                        chunkIndex++, sectionPath, ChunkType.JSON_BLOCK,
                        chunkContent, Sha256Utils.sha256Hex(chunkContent),
                        startOffset, endOffset, 1, lines.length,
                        baseMetadata != null ? baseMetadata : Map.of(),
                        Instant.now()
                    ));
                }
            }

            if (chunks.isEmpty()) {
                chunks.add(new ArtifactChunk(
                    UUID.randomUUID(), projectId, artifactId, artifactVersion,
                    0, rootName, ChunkType.JSON_BLOCK,
                    content.trim(), Sha256Utils.sha256Hex(content.trim()),
                    0, content.length(), 1, lines.length,
                    baseMetadata != null ? baseMetadata : Map.of(),
                    Instant.now()
                ));
            }
        } catch (Exception ex) {
            String[] lines = content.split("\r?\n", -1);
            chunks.add(new ArtifactChunk(
                UUID.randomUUID(), projectId, artifactId, artifactVersion,
                0, rootName, ChunkType.JSON_BLOCK,
                content.trim(), Sha256Utils.sha256Hex(content.trim()),
                0, content.length(), 1, lines.length,
                baseMetadata != null ? baseMetadata : Map.of(),
                Instant.now()
            ));
        }

        return chunks;
    }
}

