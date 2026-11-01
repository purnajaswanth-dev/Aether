package com.context_platform.aether.embedding.provider;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeterministicLocalEmbeddingProvider implements EmbeddingProvider {

    public static final String MODEL_NAME = "aether-local-deterministic-384";
    public static final String MODEL_VERSION = "v1.0";
    public static final int DIMENSION = 384;

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public String getModelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSION];
        }

        float[] vector = new float[DIMENSION];
        String cleaned = text.toLowerCase().replaceAll("[^a-z0-9_\\s-]", " ");
        String[] words = cleaned.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;

            // 1. Full word feature
            int wordHash = hashString(word, 0);
            int idx1 = Math.abs(wordHash) % DIMENSION;
            float sign1 = (wordHash & 1) == 0 ? 1.0f : -1.0f;
            vector[idx1] += sign1 * 1.5f;

            // 2. Character tri-grams for subword morphology
            if (word.length() >= 3) {
                for (int j = 0; j <= word.length() - 3; j++) {
                    String trigram = word.substring(j, j + 3);
                    int triHash = hashString(trigram, 17);
                    int idx2 = Math.abs(triHash) % DIMENSION;
                    float sign2 = (triHash & 1) == 0 ? 1.0f : -1.0f;
                    vector[idx2] += sign2 * 0.8f;
                }
            }

            // 3. Word bi-grams for local context
            if (i < words.length - 1) {
                String bigram = word + "_" + words[i + 1];
                int biHash = hashString(bigram, 31);
                int idx3 = Math.abs(biHash) % DIMENSION;
                float sign3 = (biHash & 1) == 0 ? 1.0f : -1.0f;
                vector[idx3] += sign3 * 1.2f;
            }
        }

        // L2 Normalization
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += v * v;
        }

        if (sumSquares > 0) {
            float norm = (float) Math.sqrt(sumSquares);
            for (int k = 0; k < DIMENSION; k++) {
                vector[k] /= norm;
            }
        }

        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String t : texts) {
            results.add(embed(t));
        }
        return results;
    }

    private static int hashString(String str, int seed) {
        byte[] data = str.getBytes(StandardCharsets.UTF_8);
        int h = seed ^ (data.length * 0x5bd1e995);
        for (byte b : data) {
            h ^= b & 0xff;
            h *= 0x5bd1e995;
            h ^= h >>> 15;
        }
        return h;
    }
}

