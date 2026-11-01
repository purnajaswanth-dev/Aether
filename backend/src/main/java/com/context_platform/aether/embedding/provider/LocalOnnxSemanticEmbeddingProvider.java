package com.context_platform.aether.embedding.provider;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Primary
@Component
public class LocalOnnxSemanticEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalOnnxSemanticEmbeddingProvider.class);

    public static final String MODEL_NAME = "all-MiniLM-L6-v2-onnx";
    public static final String MODEL_VERSION = "v1.0";
    public static final int DIMENSION = 384;
    private static final int MAX_SEQ_LEN = 128;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final Map<String, Long> vocab = new HashMap<>();
    private final DeterministicLocalEmbeddingProvider fallbackProvider;
    private final boolean isReady;

    public LocalOnnxSemanticEmbeddingProvider(DeterministicLocalEmbeddingProvider fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
        OrtEnvironment env = null;
        OrtSession sess = null;
        boolean ready = false;

        try {
            loadVocab();
            Path modelPath = findModelPath();
            if (modelPath != null && Files.exists(modelPath) && !vocab.isEmpty()) {
                env = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                sess = env.createSession(modelPath.toString(), opts);
                ready = true;
                log.info("Initialized local ONNX semantic embedding model from {}", modelPath);
            } else {
                log.warn("ONNX model or vocab not found; LocalOnnxSemanticEmbeddingProvider using fallback.");
            }
        } catch (Exception ex) {
            log.warn("Failed to initialize ONNX runtime session: {}. Using fallback.", ex.getMessage());
        }

        this.environment = env;
        this.session = sess;
        this.isReady = ready;
    }

    public boolean isReady() {
        return isReady;
    }

    private Path findModelPath() {
        List<Path> candidates = List.of(
            Path.of("src/main/resources/models/all-MiniLM-L6-v2/model.onnx"),
            Path.of("backend/src/main/resources/models/all-MiniLM-L6-v2/model.onnx"),
            Path.of("../backend/src/main/resources/models/all-MiniLM-L6-v2/model.onnx")
        );
        for (Path p : candidates) {
            if (Files.exists(p)) return p.toAbsolutePath();
        }
        return null;
    }

    private void loadVocab() {
        List<Path> candidates = List.of(
            Path.of("src/main/resources/models/all-MiniLM-L6-v2/vocab.txt"),
            Path.of("backend/src/main/resources/models/all-MiniLM-L6-v2/vocab.txt"),
            Path.of("../backend/src/main/resources/models/all-MiniLM-L6-v2/vocab.txt")
        );
        for (Path p : candidates) {
            if (Files.exists(p)) {
                try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    String line;
                    long idx = 0;
                    while ((line = reader.readLine()) != null) {
                        vocab.put(line.trim(), idx++);
                    }
                    log.info("Loaded {} vocabulary tokens for WordPiece tokenizer from {}", vocab.size(), p);
                    return;
                } catch (Exception e) {
                    log.warn("Failed to read vocab from {}", p, e);
                }
            }
        }
    }

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

        if (!isReady || session == null) {
            return fallbackProvider.embed(text);
        }

        try {
            long clsId = vocab.getOrDefault("[CLS]", 101L);
            long sepId = vocab.getOrDefault("[SEP]", 102L);
            long unkId = vocab.getOrDefault("[UNK]", 100L);

            List<Long> tokenIds = new ArrayList<>();
            String cleaned = text.toLowerCase().replaceAll("[^a-z0-9_-]", " ");
            String[] words = cleaned.split("\\s+");

            for (String word : words) {
                if (word.isEmpty()) continue;
                if (vocab.containsKey(word)) {
                    tokenIds.add(vocab.get(word));
                } else {
                    int start = 0;
                    while (start < word.length()) {
                        int end = word.length();
                        Long curId = null;
                        while (start < end) {
                            String sub = (start == 0) ? word.substring(start, end) : "##" + word.substring(start, end);
                            if (vocab.containsKey(sub)) {
                                curId = vocab.get(sub);
                                break;
                            }
                            end--;
                        }
                        if (curId == null) {
                            tokenIds.add(unkId);
                            break;
                        }
                        tokenIds.add(curId);
                        start = end;
                    }
                }
            }

            int actualContentTokens = Math.min(tokenIds.size(), MAX_SEQ_LEN - 2);
            long[] inputIds = new long[MAX_SEQ_LEN];
            long[] attentionMask = new long[MAX_SEQ_LEN];
            long[] tokenTypeIds = new long[MAX_SEQ_LEN];

            inputIds[0] = clsId;
            attentionMask[0] = 1L;

            for (int i = 0; i < actualContentTokens; i++) {
                inputIds[i + 1] = tokenIds.get(i);
                attentionMask[i + 1] = 1L;
            }

            inputIds[actualContentTokens + 1] = sepId;
            attentionMask[actualContentTokens + 1] = 1L;
            int totalSeqLen = actualContentTokens + 2;

            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), new long[]{1, MAX_SEQ_LEN});
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), new long[]{1, MAX_SEQ_LEN});
            OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(tokenTypeIds), new long[]{1, MAX_SEQ_LEN});

            Map<String, OnnxTensor> inputs = Map.of(
                "input_ids", inputIdsTensor,
                "attention_mask", attentionMaskTensor,
                "token_type_ids", tokenTypeIdsTensor
            );

            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] output = (float[][][]) result.get(0).getValue();
                float[] meanPooled = new float[DIMENSION];

                for (int i = 0; i < totalSeqLen; i++) {
                    for (int d = 0; d < DIMENSION; d++) {
                        meanPooled[d] += output[0][i][d];
                    }
                }

                if (totalSeqLen > 0) {
                    for (int d = 0; d < DIMENSION; d++) {
                        meanPooled[d] /= totalSeqLen;
                    }
                }

                // L2 normalization
                double sumSquares = 0.0;
                for (float v : meanPooled) sumSquares += v * v;
                if (sumSquares > 0) {
                    float norm = (float) Math.sqrt(sumSquares);
                    for (int d = 0; d < DIMENSION; d++) {
                        meanPooled[d] /= norm;
                    }
                }

                return meanPooled;
            } finally {
                inputIdsTensor.close();
                attentionMaskTensor.close();
                tokenTypeIdsTensor.close();
            }
        } catch (Exception ex) {
            log.warn("ONNX embedding inference failed: {}. Falling back to deterministic provider.", ex.getMessage());
            return fallbackProvider.embed(text);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String t : texts) {
            results.add(embed(t));
        }
        return results;
    }
}
