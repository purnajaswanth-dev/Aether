package com.context_platform.aether.embedding.repository;

import com.context_platform.aether.embedding.model.ChunkEmbedding;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.ChunkType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.*;

@Repository
public class EmbeddingRepository {

    public record VectorSearchResult(
        UUID chunkId,
        UUID artifactId,
        int artifactVersion,
        String parentSectionPath,
        ChunkType chunkType,
        String content,
        String contentHash,
        int startOffset,
        int endOffset,
        int startLine,
        int endLine,
        double similarity
    ) {}

    private final JdbcClient jdbcClient;

    public EmbeddingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(ChunkEmbedding embedding) {
        String vectorStr = formatVector(embedding.embedding());
        jdbcClient.sql("""
            INSERT INTO embeddings (id, project_id, chunk_id, model_name, model_version, dimension, embedding, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?)
            ON CONFLICT (id) DO NOTHING
            """)
            .params(
                embedding.id(),
                embedding.projectId(),
                embedding.chunkId(),
                embedding.modelName(),
                embedding.modelVersion(),
                embedding.dimension(),
                vectorStr,
                Timestamp.from(embedding.createdAt())
            )
            .update();
    }

    public void saveAll(List<ChunkEmbedding> embeddings) {
        for (ChunkEmbedding e : embeddings) {
            save(e);
        }
    }

    public List<VectorSearchResult> searchSimilar(UUID projectId, float[] queryVector, int limit) {
        String vectorStr = formatVector(queryVector);
        return jdbcClient.sql("""
            SELECT e.chunk_id, c.artifact_id, c.artifact_version, c.parent_section_path,
                   c.chunk_type, c.content, c.content_hash, c.start_offset, c.end_offset,
                   c.start_line, c.end_line,
                   (1 - (e.embedding <=> ?::vector)) as similarity
            FROM embeddings e
            JOIN artifact_chunks c ON e.chunk_id = c.id
            WHERE e.project_id = ?
            ORDER BY e.embedding <=> ?::vector ASC
            LIMIT ?
            """)
            .params(vectorStr, projectId, vectorStr, limit)
            .query((rs, rowNum) -> new VectorSearchResult(
                rs.getObject("chunk_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getInt("artifact_version"),
                rs.getString("parent_section_path"),
                ChunkType.valueOf(rs.getString("chunk_type")),
                rs.getString("content"),
                rs.getString("content_hash"),
                rs.getInt("start_offset"),
                rs.getInt("end_offset"),
                rs.getInt("start_line"),
                rs.getInt("end_line"),
                rs.getDouble("similarity")
            ))
            .list();
    }

    public void deleteByArtifactVersion(UUID projectId, UUID artifactId, int version) {
        jdbcClient.sql("""
            DELETE FROM embeddings e
            USING artifact_chunks c
            WHERE e.chunk_id = c.id AND c.project_id = ? AND c.artifact_id = ? AND c.artifact_version = ?
            """)
            .params(projectId, artifactId, version)
            .update();
    }

    public void deleteByProjectId(UUID projectId) {
        jdbcClient.sql("DELETE FROM embeddings WHERE project_id = ?")
            .params(projectId)
            .update();
    }

    public static String formatVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}

