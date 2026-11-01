package com.context_platform.aether.extraction.service;

import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.model.ValidationState;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MeaningPreservationValidator {

    public record ValidationResult(
        ValidationState state,
        String notes
    ) {}

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)*\\b");
    private static final List<String> MODAL_WORDS = List.of(
        "never", "must", "cannot", "unless", "except", "only if", "forbidden", "should", "not"
    );
    private static final List<String> TECH_KEYWORDS = List.of(
        "postgresql", "postgres", "pgvector", "spring boot", "react", "docker", "flyway", "java", "typescript",
        "redis", "kafka", "mysql", "mongodb", "aws", "gcp", "azure", "render", "github"
    );

    public ValidationResult validate(String fullContent, ExtractedFact fact) {
        List<String> failureReasons = new ArrayList<>();

        // Rule (a): Supporting quote exists verbatim at claimed offsets
        if (fullContent == null || fact.startOffset() < 0 || fact.endOffset() > fullContent.length() || fact.startOffset() >= fact.endOffset()) {
            failureReasons.add("Invalid character offsets: [" + fact.startOffset() + ", " + fact.endOffset() + "]");
        } else {
            String actualSlice = fullContent.substring(fact.startOffset(), fact.endOffset());
            if (!actualSlice.equals(fact.exactQuote())) {
                failureReasons.add("Quote mismatch at offsets: expected '" + fact.exactQuote() + "', found '" + actualSlice + "'");
            }
        }

        String quoteLower = fact.exactQuote().toLowerCase();
        String factLower = fact.factStatement().toLowerCase();

        // Rule (b): Numbers, versions, technology names and negations in the source sentence appear in fact
        Matcher numberMatcher = NUMBER_PATTERN.matcher(fact.exactQuote());
        while (numberMatcher.find()) {
            String num = numberMatcher.group();
            if (!fact.factStatement().contains(num)) {
                failureReasons.add("Number or version '" + num + "' from quote missing in extracted statement");
            }
        }

        for (String tech : TECH_KEYWORDS) {
            if (quoteLower.contains(tech) && !factLower.contains(tech)) {
                failureReasons.add("Technology entity '" + tech + "' from quote missing in extracted statement");
            }
        }

        // Rule (c): Conditions and exceptions retained
        for (String modal : MODAL_WORDS) {
            if (quoteLower.contains(modal) && !factLower.contains(modal)) {
                failureReasons.add("Critical modal condition/exception '" + modal + "' was omitted in extracted statement");
            }
        }

        if (!failureReasons.isEmpty()) {
            return new ValidationResult(
                ValidationState.UNVERIFIED,
                "Meaning preservation validation failed: " + String.join("; ", failureReasons)
            );
        }

        return new ValidationResult(ValidationState.VERIFIED, "Verbatim quote verified; numbers, entities, and conditions preserved.");
    }
}

