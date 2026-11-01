package com.context_platform.aether.embedding.service;

import com.context_platform.aether.embedding.model.ChunkEmbedding;
import com.context_platform.aether.embedding.provider.EmbeddingProvider;
import com.context_platform.aether.embedding.repository.EmbeddingRepository;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EmbeddingService {

    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingRepository embeddingRepository;

    public EmbeddingService(EmbeddingProvider embeddingProvider, EmbeddingRepository embeddingRepository) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingRepository = embeddingRepository;
    }

    public void generateAndStoreEmbeddings(UUID projectId, List<ArtifactChunk> chunks) {
        generateAndStoreEmbeddings(projectId, chunks, this.embeddingProvider);
    }

    public void generateAndStoreEmbeddings(UUID projectId, List<ArtifactChunk> chunks, EmbeddingProvider provider) {
        if (chunks == null || chunks.isEmpty()) return;

        List<ChunkEmbedding> embeddings = new ArrayList<>(chunks.size());
        for (ArtifactChunk chunk : chunks) {
            float[] vector = provider.embed(chunk.content());
            embeddings.add(new ChunkEmbedding(
                UUID.randomUUID(),
                projectId,
                chunk.id(),
                provider.getModelName(),
                provider.getModelVersion(),
                provider.getDimension(),
                vector,
                Instant.now()
            ));
        }

        embeddingRepository.saveAll(embeddings);
    }

    public void reembedProject(UUID projectId, List<ArtifactChunk> chunks, EmbeddingProvider provider) {
        embeddingRepository.deleteByProjectId(projectId);
        generateAndStoreEmbeddings(projectId, chunks, provider);
    }

    public EmbeddingProvider getEmbeddingProvider() {
        return embeddingProvider;
    }
}

