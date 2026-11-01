package com.context_platform.aether.retrieval.service;

import com.context_platform.aether.embedding.provider.EmbeddingProvider;
import com.context_platform.aether.embedding.repository.EmbeddingRepository;
import com.context_platform.aether.extraction.model.DependencyEdge;
import com.context_platform.aether.extraction.service.DependencyEdgeRepository;
import com.context_platform.aether.ingestion.model.ChunkType;
import com.context_platform.aether.mcp.service.McpToolRegistry;
import com.context_platform.aether.retrieval.model.SearchResultItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class HybridRetrievalEngine {

    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingRepository embeddingRepository;
    private final DependencyEdgeRepository edgeRepository;
    private final JdbcClient jdbcClient;

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public HybridRetrievalEngine(
        EmbeddingProvider embeddingProvider,
        EmbeddingRepository embeddingRepository,
        DependencyEdgeRepository edgeRepository,
        JdbcClient jdbcClient
    ) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingRepository = embeddingRepository;
        this.edgeRepository = edgeRepository;
        this.jdbcClient = jdbcClient;
    }

    public HybridRetrievalEngine withEmbeddingProvider(EmbeddingProvider customProvider) {
        return new HybridRetrievalEngine(customProvider, this.embeddingRepository, this.edgeRepository, this.jdbcClient);
    }

    public List<SearchResultItem> search(UUID projectId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Map chunkId -> scoring & item metadata
        Map<UUID, ChunkCandidate> candidates = new HashMap<>();

        // 1. Semantic Search (pgvector)
        float[] queryVector = embeddingProvider.embed(query);
        List<EmbeddingRepository.VectorSearchResult> semanticResults = embeddingRepository.searchSimilar(projectId, queryVector, limit * 2);
        for (int rank = 0; rank < semanticResults.size(); rank++) {
            var res = semanticResults.get(rank);
            ChunkCandidate cand = candidates.computeIfAbsent(res.chunkId(), id -> new ChunkCandidate(
                res.chunkId(), res.artifactId(), res.artifactVersion(), res.parentSectionPath(),
                res.chunkType(), res.content(), res.contentHash(), res.startOffset(), res.endOffset(),
                res.startLine(), res.endLine()
            ));
            cand.addRrfScore("SEMANTIC", 1.2, rank + 1);
        }

        // 2. Keyword Search (PostgreSQL full-text + trigram similarity)
        List<KeywordRow> keywordResults = searchKeyword(projectId, query, limit * 2);
        for (int rank = 0; rank < keywordResults.size(); rank++) {
            var res = keywordResults.get(rank);
            ChunkCandidate cand = candidates.computeIfAbsent(res.chunkId(), id -> new ChunkCandidate(
                res.chunkId(), res.artifactId(), res.artifactVersion(), res.parentSectionPath(),
                res.chunkType(), res.content(), res.contentHash(), res.startOffset(), res.endOffset(),
                res.startLine(), res.endLine()
            ));
            cand.addRrfScore("KEYWORD", 1.0, rank + 1);
        }

        // 3. Structured SQL (Decisions and active artifacts)
        List<StructuredRow> structuredResults = searchStructured(projectId, query, limit);
        for (int rank = 0; rank < structuredResults.size(); rank++) {
            var res = structuredResults.get(rank);
            ChunkCandidate cand = candidates.computeIfAbsent(res.chunkId(), id -> new ChunkCandidate(
                res.chunkId(), res.artifactId(), res.artifactVersion(), res.parentSectionPath(),
                res.chunkType(), res.content(), res.contentHash(), res.startOffset(), res.endOffset(),
                res.startLine(), res.endLine()
            ));
            cand.addRrfScore("STRUCTURED_SQL", 1.1, rank + 1);
            cand.approvalState = res.decisionState();
            cand.isSuperseded = "SUPERSEDED".equalsIgnoreCase(res.decisionState()) || res.isHistoricalVersion();
        }

        // 4. Graph Edge Expansion (reinforce candidates linked by knowledge edges)
        List<UUID> candidateArtifacts = candidates.values().stream()
            .map(c -> c.artifactId)
            .distinct()
            .limit(3)
            .toList();
        for (UUID artId : candidateArtifacts) {
            List<DependencyEdge> edges = edgeRepository.findConnectedGraph(projectId, artId, 1);
            for (int rank = 0; rank < edges.size(); rank++) {
                DependencyEdge edge = edges.get(rank);
                UUID targetArtId = edge.targetId();
                if (targetArtId != null && !targetArtId.equals(artId)) {
                    for (ChunkCandidate existing : candidates.values()) {
                        if (existing.artifactId.equals(targetArtId)) {
                            existing.addRrfScore("GRAPH", 0.8, rank + 1);
                        }
                    }
                }
            }
        }

        // 5. Exact Retrieval (UUID or exact code symbol/identifier lookup)
        if (UUID_PATTERN.matcher(query.trim()).matches()) {
            UUID exactId = UUID.fromString(query.trim());
            List<ExactRow> exactRows = searchExact(projectId, exactId);
            for (int rank = 0; rank < exactRows.size(); rank++) {
                var res = exactRows.get(rank);
                ChunkCandidate cand = candidates.computeIfAbsent(res.chunkId(), id -> new ChunkCandidate(
                    res.chunkId(), res.artifactId(), res.artifactVersion(), res.parentSectionPath(),
                    res.chunkType(), res.content(), res.contentHash(), res.startOffset(), res.endOffset(),
                    res.startLine(), res.endLine()
                ));
                cand.addRrfScore("EXACT", 2.0, rank + 1);
            }
        } else {
            // Check for exact symbol / identifier tokens in the query
            for (String rawToken : query.split("\\s+")) {
                String token = rawToken.replaceAll("[^a-zA-Z0-9_-]", "").trim();
                if (token.length() >= 4 && (token.contains("_") || token.contains("-") || Character.isUpperCase(token.charAt(0)))) {
                    List<ExactRow> tokenRows = searchExactByToken(projectId, token);
                    for (int rank = 0; rank < tokenRows.size(); rank++) {
                        var res = tokenRows.get(rank);
                        ChunkCandidate cand = candidates.computeIfAbsent(res.chunkId(), id -> new ChunkCandidate(
                            res.chunkId(), res.artifactId(), res.artifactVersion(), res.parentSectionPath(),
                            res.chunkType(), res.content(), res.contentHash(), res.startOffset(), res.endOffset(),
                            res.startLine(), res.endLine()
                        ));
                        cand.addRrfScore("EXACT", 2.0, rank + 1);
                    }
                }
            }
        }

        // 6. Check decision state and version status for all remaining candidates
        enrichCandidateStates(projectId, candidates.values());

        // Re-ranking step
        List<SearchResultItem> results = new ArrayList<>();
        String queryLower = query.toLowerCase();

        for (ChunkCandidate c : candidates.values()) {
            double finalScore = c.totalRrfScore;

            // Approval state priority: APPROVED beats PROPOSED, and strictly beats SUPERSEDED
            if ("APPROVED".equalsIgnoreCase(c.approvalState)) {
                finalScore += 0.30;
            } else if ("PROPOSED".equalsIgnoreCase(c.approvalState)) {
                finalScore += 0.10;
            }

            if (c.isSuperseded) {
                finalScore -= 0.50; // Severe penalty for superseded or historical content
            }

            // Path relevance boost
            if (c.parentSectionPath != null && c.parentSectionPath.toLowerCase().contains(queryLower)) {
                finalScore += 0.15;
            }

            String stateLabel = c.isSuperseded ? "SUPERSEDED" : (c.approvalState != null ? c.approvalState : "UNVERIFIED");
            String untrusted = McpToolRegistry.formatUntrustedData(
                c.artifactId.toString(), c.artifactVersion, c.contentHash, "AETHER_HYBRID_RAG", c.content
            );

            results.add(new SearchResultItem(
                c.chunkId, c.artifactId, c.artifactVersion,
                c.parentSectionPath, c.parentSectionPath, c.chunkType,
                c.content, c.contentHash, c.startOffset, c.endOffset,
                c.startLine, c.endLine, stateLabel, c.isSuperseded,
                finalScore, String.join(",", c.sourceMethods), untrusted
            ));
        }

        // Sort descending by final score
        results.sort(Comparator.comparingDouble(SearchResultItem::rrfScore).reversed());

        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }

    private static final Set<String> STOP_WORDS = Set.of(
        "what", "when", "where", "which", "who", "whom", "this", "that", "these", "those",
        "with", "from", "have", "has", "had", "does", "done", "doing", "will", "would",
        "shall", "should", "could", "about", "into", "than", "then", "their", "there",
        "they", "been", "being", "used", "using", "uses"
    );

    private String buildOrQuery(String query) {
        if (query == null || query.isBlank()) return "aether";
        String[] words = query.split("\\s+");
        List<String> terms = new ArrayList<>();
        for (String w : words) {
            String clean = w.replaceAll("[^a-zA-Z0-9_-]", "").trim();
            if (clean.length() >= 3 && !STOP_WORDS.contains(clean.toLowerCase())) {
                terms.add(clean);
            }
        }
        if (terms.isEmpty()) {
            return query;
        }
        return String.join(" or ", terms);
    }

    private String extractLongestToken(String query) {
        if (query == null || query.isBlank()) return "";
        String[] words = query.split("\\s+");
        String longest = "";
        for (String w : words) {
            String clean = w.replaceAll("[^a-zA-Z0-9_-]", "").trim();
            if (clean.length() > longest.length()) {
                longest = clean;
            }
        }
        return longest;
    }

    private List<KeywordRow> searchKeyword(UUID projectId, String query, int limit) {
        String orQuery = buildOrQuery(query);
        String longestToken = extractLongestToken(query);
        String tokenPattern = "%" + longestToken + "%";

        return jdbcClient.sql("""
            SELECT id as chunk_id, artifact_id, artifact_version, parent_section_path,
                   chunk_type, content, content_hash, start_offset, end_offset, start_line, end_line,
                   (
                       COALESCE(ts_rank_cd(tsv, plainto_tsquery('english', ?)), 0.0) * 2.0
                       + COALESCE(ts_rank_cd(tsv, websearch_to_tsquery('english', ?)), 0.0)
                       + similarity(content, ?)
                       + (CASE WHEN lower(content) LIKE lower(?) THEN 0.5 ELSE 0.0 END)
                       + (CASE WHEN lower(parent_section_path) LIKE lower(?) THEN 0.3 ELSE 0.0 END)
                   ) as score
            FROM artifact_chunks
            WHERE project_id = ?
              AND (
                  tsv @@ plainto_tsquery('english', ?)
                  OR tsv @@ websearch_to_tsquery('english', ?)
                  OR similarity(content, ?) > 0.05
                  OR lower(content) LIKE lower(?)
                  OR lower(parent_section_path) LIKE lower(?)
              )
            ORDER BY score DESC
            LIMIT ?
            """)
            .params(
                query, orQuery, query, tokenPattern, tokenPattern,
                projectId,
                query, orQuery, query, tokenPattern, tokenPattern,
                limit
            )
            .query((rs, rowNum) -> new KeywordRow(
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
                rs.getInt("end_line")
            ))
            .list();
    }

    private List<StructuredRow> searchStructured(UUID projectId, String query, int limit) {
        String token = extractLongestToken(query);
        String pattern = "%" + (token.isBlank() ? query.trim().toLowerCase() : token.toLowerCase()) + "%";
        return jdbcClient.sql("""
            SELECT c.id as chunk_id, c.artifact_id, c.artifact_version, c.parent_section_path,
                   c.chunk_type, c.content, c.content_hash, c.start_offset, c.end_offset,
                   c.start_line, c.end_line,
                   d.state as decision_state,
                   (c.artifact_version < (SELECT COALESCE(MAX(v.version), 1) FROM artifact_versions v WHERE v.artifact_id = c.artifact_id AND v.project_id = c.project_id)) as is_historical_version
            FROM artifact_chunks c
            JOIN artifacts a ON c.artifact_id = a.id
            LEFT JOIN technical_decisions d ON (d.project_id = c.project_id AND lower(d.title) LIKE ?)
            WHERE c.project_id = ? AND (lower(c.parent_section_path) LIKE ? OR lower(a.title) LIKE ? OR lower(c.content) LIKE ?)
            ORDER BY (d.state = 'APPROVED') DESC, c.artifact_version DESC
            LIMIT ?
            """)
            .params(pattern, projectId, pattern, pattern, pattern, limit)
            .query((rs, rowNum) -> new StructuredRow(
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
                rs.getString("decision_state"),
                rs.getBoolean("is_historical_version")
            ))
            .list();
    }

    private List<ExactRow> searchExact(UUID projectId, UUID exactId) {
        return jdbcClient.sql("""
            SELECT id as chunk_id, artifact_id, artifact_version, parent_section_path,
                   chunk_type, content, content_hash, start_offset, end_offset, start_line, end_line
            FROM artifact_chunks
            WHERE project_id = ? AND (id = ? OR artifact_id = ?)
            ORDER BY chunk_index ASC
            LIMIT 10
            """)
            .params(projectId, exactId, exactId)
            .query((rs, rowNum) -> new ExactRow(
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
                rs.getInt("end_line")
            ))
            .list();
    }

    private List<ExactRow> searchExactByToken(UUID projectId, String token) {
        String pattern = "%" + token + "%";
        return jdbcClient.sql("""
            SELECT id as chunk_id, artifact_id, artifact_version, parent_section_path,
                   chunk_type, content, content_hash, start_offset, end_offset, start_line, end_line
            FROM artifact_chunks
            WHERE project_id = ? AND (content ILIKE ? OR parent_section_path ILIKE ?)
            ORDER BY chunk_index ASC
            LIMIT 5
            """)
            .params(projectId, pattern, pattern)
            .query((rs, rowNum) -> new ExactRow(
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
                rs.getInt("end_line")
            ))
            .list();
    }

    private void enrichCandidateStates(UUID projectId, Collection<ChunkCandidate> candidates) {
        for (ChunkCandidate c : candidates) {
            if (c.approvalState == null) {
                // Check if linked artifact is superseded by newer version
                Integer currentVersion = jdbcClient.sql("SELECT COALESCE(MAX(version), 1) FROM artifact_versions WHERE artifact_id = ? AND project_id = ?")
                    .params(c.artifactId, projectId)
                    .query(Integer.class)
                    .optional()
                    .orElse(c.artifactVersion);

                if (c.artifactVersion < currentVersion) {
                    c.isSuperseded = true;
                    c.approvalState = "SUPERSEDED";
                } else {
                    c.approvalState = "VERIFIED";
                }
            }
        }
    }

    private static class ChunkCandidate {
        final UUID chunkId;
        final UUID artifactId;
        final int artifactVersion;
        final String parentSectionPath;
        final ChunkType chunkType;
        final String content;
        final String contentHash;
        final int startOffset;
        final int endOffset;
        final int startLine;
        final int endLine;
        double totalRrfScore = 0.0;
        final Set<String> sourceMethods = new LinkedHashSet<>();
        String approvalState = null;
        boolean isSuperseded = false;

        ChunkCandidate(
            UUID chunkId, UUID artifactId, int artifactVersion, String parentSectionPath,
            ChunkType chunkType, String content, String contentHash,
            int startOffset, int endOffset, int startLine, int endLine
        ) {
            this.chunkId = chunkId;
            this.artifactId = artifactId;
            this.artifactVersion = artifactVersion;
            this.parentSectionPath = parentSectionPath;
            this.chunkType = chunkType;
            this.content = content;
            this.contentHash = contentHash;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        void addRrfScore(String method, double weight, int rank) {
            sourceMethods.add(method);
            totalRrfScore += (weight / (60.0 + rank));
        }
    }

    private record KeywordRow(UUID chunkId, UUID artifactId, int artifactVersion, String parentSectionPath, ChunkType chunkType, String content, String contentHash, int startOffset, int endOffset, int startLine, int endLine) {}
    private record StructuredRow(UUID chunkId, UUID artifactId, int artifactVersion, String parentSectionPath, ChunkType chunkType, String content, String contentHash, int startOffset, int endOffset, int startLine, int endLine, String decisionState, boolean isHistoricalVersion) {}
    private record ExactRow(UUID chunkId, UUID artifactId, int artifactVersion, String parentSectionPath, ChunkType chunkType, String content, String contentHash, int startOffset, int endOffset, int startLine, int endLine) {}
}

