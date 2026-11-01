package com.context_platform.aether.embedding.provider;

import java.util.List;

public interface EmbeddingProvider {
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
    String getModelName();
    String getModelVersion();
    int getDimension();
}

