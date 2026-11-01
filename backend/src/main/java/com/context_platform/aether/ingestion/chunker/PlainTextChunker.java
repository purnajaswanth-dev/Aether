package com.context_platform.aether.ingestion.chunker;

import com.context_platform.aether.common.Sha256Utils;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.ChunkType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class PlainTextChunker implements Chunker {

    @Override
    public boolean supports(String artifactType, String title) {
        return true; // Fallback chunker
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
        String[] paragraphs = content.split("(\\r?\\n){2,}");
        String baseName = title != null ? title : "Document";

        int currentOffset = 0;
        int currentLine = 1;
        int chunkIndex = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            int startOffset = content.indexOf(para, currentOffset);
            if (startOffset == -1) startOffset = currentOffset;
            int endOffset = startOffset + para.length();
            int lineCount = (int) para.lines().count();
            int endLine = currentLine + lineCount - 1;

            String path = baseName + " > Paragraph " + (chunkIndex + 1);
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("paragraph_index", chunkIndex + 1);

            String hash = Sha256Utils.sha256Hex(trimmed);
            chunks.add(new ArtifactChunk(
                UUID.randomUUID(), projectId, artifactId, artifactVersion, chunkIndex++,
                path, ChunkType.PLAIN_PARAGRAPH, trimmed, hash,
                startOffset, endOffset, currentLine, endLine, metadata, Instant.now()
            ));

            currentOffset = endOffset;
            currentLine = endLine + 2;
        }

        if (chunks.isEmpty()) {
            String trimmed = content.trim();
            String hash = Sha256Utils.sha256Hex(trimmed);
            chunks.add(new ArtifactChunk(
                UUID.randomUUID(), projectId, artifactId, artifactVersion, 0,
                baseName, ChunkType.PLAIN_PARAGRAPH, trimmed, hash,
                0, content.length(), 1, (int) content.lines().count(), baseMetadata, Instant.now()
            ));
        }

        return chunks;
    }
}
