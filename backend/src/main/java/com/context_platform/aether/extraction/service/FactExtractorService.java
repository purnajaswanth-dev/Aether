package com.context_platform.aether.extraction.service;

import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.model.FactCategory;
import com.context_platform.aether.extraction.model.ValidationState;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FactExtractorService {

    private final MeaningPreservationValidator validator;

    public FactExtractorService(MeaningPreservationValidator validator) {
        this.validator = validator;
    }

    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile("(?i)(?:must|shall|should|enforce|require|support|use|never|cannot)\\s+([^.;\n]+)");
    private static final Pattern DECISION_PATTERN = Pattern.compile("(?i)(?:decision|decided|we will use|chosen|we adopt|adopted)\\s+([^.;\n]+)");
    private static final Pattern DATABASE_PATTERN = Pattern.compile("(?i)(?:table|schema|column|database|foreign-key|index|rls|row-level security)\\s+([^.;\n]+)");
    private static final Pattern ARCH_PATTERN = Pattern.compile("(?i)(?:architecture|component|monolith|microservice|service|backend|frontend)\\s+([^.;\n]+)");

    public List<ExtractedFact> extractFacts(
        UUID projectId,
        UUID artifactId,
        int artifactVersion,
        String content,
        String sourcePlatform,
        String extractionMethod
    ) {
        if (content == null || content.isBlank()) return List.of();

        List<ExtractedFact> rawFacts = new ArrayList<>();
        String[] lines = content.split("\r?\n");
        int currentOffset = 0;

        for (String line : lines) {
            int lineLen = line.length() + 1;
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("```")) {
                currentOffset += lineLen;
                continue;
            }

            // Check compound multi-requirement sentences (Rule d)
            // e.g. "Use PostgreSQL, enforce foreign-key constraints and never expose database credentials to the frontend"
            if (trimmed.contains(",") || trimmed.contains(" and ") || trimmed.contains(";")) {
                List<ExtractedFact> splitFacts = extractCompoundClauses(
                    projectId, artifactId, artifactVersion, content, line, currentOffset, sourcePlatform, extractionMethod
                );
                if (!splitFacts.isEmpty()) {
                    rawFacts.addAll(splitFacts);
                    currentOffset += lineLen;
                    continue;
                }
            }

            // Single requirement / statement extraction
            ExtractedFact fact = extractSingleFactFromLine(
                projectId, artifactId, artifactVersion, content, line, currentOffset, sourcePlatform, extractionMethod
            );
            if (fact != null) {
                rawFacts.add(fact);
            }

            currentOffset += lineLen;
        }

        // Run meaning-preservation validation on every fact (fails closed as UNVERIFIED if check fails)
        List<ExtractedFact> validatedFacts = new ArrayList<>(rawFacts.size());
        for (ExtractedFact raw : rawFacts) {
            MeaningPreservationValidator.ValidationResult vResult = validator.validate(content, raw);
            validatedFacts.add(new ExtractedFact(
                raw.id(),
                raw.projectId(),
                raw.artifactId(),
                raw.artifactVersion(),
                raw.category(),
                raw.factKey(),
                raw.factStatement(),
                raw.exactQuote(),
                raw.startOffset(),
                raw.endOffset(),
                raw.sourcePlatform(),
                raw.extractionMethod(),
                vResult.state(),
                vResult.notes(),
                raw.approvalState(), // strictly PROPOSED
                raw.confidenceScore(),
                null,
                null,
                raw.createdAt()
            ));
        }

        return validatedFacts;
    }

    private List<ExtractedFact> extractCompoundClauses(
        UUID projectId, UUID artifactId, int version, String fullContent,
        String line, int lineStartOffset, String platform, String method
    ) {
        // Split on commas, semicolons, and coordinate conjunctions (" and ")
        String[] clauses = line.split(",\\s*|\\s+and\\s+|;\\s*");
        if (clauses.length <= 1) return List.of();

        List<ExtractedFact> facts = new ArrayList<>();
        int searchFrom = 0;

        for (String clause : clauses) {
            String trimmedClause = clause.trim();
            if (trimmedClause.endsWith(".")) {
                trimmedClause = trimmedClause.substring(0, trimmedClause.length() - 1).trim();
            }
            if (trimmedClause.length() < 5) continue;

            // Find exact location of this clause in the line
            int clauseIndexInLine = line.indexOf(trimmedClause, searchFrom);
            if (clauseIndexInLine == -1) clauseIndexInLine = line.indexOf(trimmedClause);
            if (clauseIndexInLine == -1) continue;

            int start = lineStartOffset + clauseIndexInLine;
            int end = start + trimmedClause.length();
            searchFrom = clauseIndexInLine + trimmedClause.length();

            FactCategory category = categorizeClause(trimmedClause);
            String key = makeKey(trimmedClause);

            facts.add(new ExtractedFact(
                UUID.randomUUID(), projectId, artifactId, version,
                category, key, trimmedClause, trimmedClause,
                start, end, platform, method,
                ValidationState.UNVERIFIED, null, "PROPOSED", 0.95,
                null, null, Instant.now()
            ));
        }

        return facts;
    }

    private ExtractedFact extractSingleFactFromLine(
        UUID projectId, UUID artifactId, int version, String fullContent,
        String line, int lineStartOffset, String platform, String method
    ) {
        String trimmed = line.trim();
        if (trimmed.length() < 10) return null;

        FactCategory category = categorizeClause(trimmed);
        int start = lineStartOffset + line.indexOf(trimmed);
        int end = start + trimmed.length();
        String key = makeKey(trimmed);

        return new ExtractedFact(
            UUID.randomUUID(), projectId, artifactId, version,
            category, key, trimmed, trimmed,
            start, end, platform, method,
            ValidationState.UNVERIFIED, null, "PROPOSED", 0.90,
            null, null, Instant.now()
        );
    }

    private FactCategory categorizeClause(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("never") || lower.contains("must not") || lower.contains("cannot") || lower.contains("forbidden") || lower.contains("enforce") || lower.contains("constraint")) {
            return FactCategory.CONSTRAINT;
        }
        if (lower.contains("table") || lower.contains("database") || lower.contains("schema") || lower.contains("foreign-key") || lower.contains("sql")) {
            return FactCategory.DATABASE_DESIGN;
        }
        if (lower.contains("decision") || lower.contains("decided") || lower.contains("adopt") || lower.contains("we will use")) {
            return FactCategory.DECISION;
        }
        if (lower.contains("api") || lower.contains("endpoint") || lower.contains("contract") || lower.contains("request") || lower.contains("json-rpc")) {
            return FactCategory.API_CONTRACT;
        }
        if (lower.contains("architecture") || lower.contains("component") || lower.contains("monolith") || lower.contains("engine")) {
            return FactCategory.ARCHITECTURE_COMPONENT;
        }
        return FactCategory.REQUIREMENT;
    }

    private String makeKey(String text) {
        String clean = text.replaceAll("[^A-Za-z0-9\\s]", " ").trim();
        String[] words = clean.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, words.length); i++) {
            if (i > 0) sb.append("-");
            sb.append(words[i].toLowerCase());
        }
        return sb.toString();
    }
}

