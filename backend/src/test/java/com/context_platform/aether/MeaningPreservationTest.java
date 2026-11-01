package com.context_platform.aether;

import com.context_platform.aether.extraction.model.ExtractedFact;
import com.context_platform.aether.extraction.model.FactCategory;
import com.context_platform.aether.extraction.model.ValidationState;
import com.context_platform.aether.extraction.service.FactExtractorService;
import com.context_platform.aether.extraction.service.MeaningPreservationValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3: Meaning-Preservation Engine & Provenance Tests")
public class MeaningPreservationTest {

    private final MeaningPreservationValidator validator = new MeaningPreservationValidator();
    private final FactExtractorService extractor = new FactExtractorService(validator);

    private final UUID projectId = UUID.randomUUID();
    private final UUID artifactId = UUID.randomUUID();

    @Test
    @DisplayName("Gate Test: PostgreSQL compound sentence yields exactly three verified facts with quotes")
    void testPostgreSqlExampleYieldsThreeFacts() {
        String content = "Use PostgreSQL, enforce foreign-key constraints and never expose database credentials to the frontend.";

        List<ExtractedFact> facts = extractor.extractFacts(
            projectId, artifactId, 1, content, "CLAUDE", "rule-based-v1"
        );

        // Must yield 3 distinct facts
        assertEquals(3, facts.size(), "One source sentence with three requirements must yield three facts, not one");

        // Verify Fact 1: Use PostgreSQL
        ExtractedFact f1 = facts.get(0);
        assertEquals("Use PostgreSQL", f1.exactQuote());
        assertEquals(ValidationState.VERIFIED, f1.validationState());
        assertTrue(f1.factStatement().contains("PostgreSQL"));

        // Verify Fact 2: enforce foreign-key constraints
        ExtractedFact f2 = facts.get(1);
        assertEquals("enforce foreign-key constraints", f2.exactQuote());
        assertEquals(ValidationState.VERIFIED, f2.validationState());
        assertTrue(f2.factStatement().contains("foreign-key"));

        // Verify Fact 3: never expose database credentials to the frontend
        ExtractedFact f3 = facts.get(2);
        assertEquals("never expose database credentials to the frontend", f3.exactQuote());
        assertEquals(ValidationState.VERIFIED, f3.validationState());
        assertTrue(f3.factStatement().contains("never"), "Must preserve critical negation 'never'");
        assertEquals(FactCategory.CONSTRAINT, f3.category());
    }

    @Test
    @DisplayName("Gate Test: Deliberately mutated extraction fails validation and is stored UNVERIFIED")
    void testMutatedExtractionFailsValidation() {
        String content = "Use PostgreSQL 16 with pgvector and never bypass row-level security unless authorized.";

        // 1. Mutated statement: missing negation 'never' and version '16'
        ExtractedFact mutatedFact = new ExtractedFact(
            UUID.randomUUID(), projectId, artifactId, 1,
            FactCategory.REQUIREMENT, "db-requirement",
            "Use PostgreSQL with pgvector and bypass row-level security", // 'never' removed, '16' removed!
            "Use PostgreSQL 16 with pgvector and never bypass row-level security unless authorized.",
            0, content.length(), "CLAUDE", "test-model",
            ValidationState.UNVERIFIED, null, "PROPOSED", 1.0,
            null, null, Instant.now()
        );

        MeaningPreservationValidator.ValidationResult result = validator.validate(content, mutatedFact);

        assertEquals(ValidationState.UNVERIFIED, result.state(), "Mutated fact must fail validation and be tagged UNVERIFIED");
        assertNotNull(result.notes());
        assertTrue(result.notes().contains("16") || result.notes().contains("never") || result.notes().contains("unless"),
            "Validation notes must detail omitted numbers, negations, or modal conditions");
    }

    @Test
    @DisplayName("Quote offset mismatch fails closed as UNVERIFIED")
    void testQuoteOffsetMismatchFailsClosed() {
        String content = "Spring Boot 3.3.5 is the required monolith framework.";

        ExtractedFact badOffsetFact = new ExtractedFact(
            UUID.randomUUID(), projectId, artifactId, 1,
            FactCategory.REQUIREMENT, "framework",
            "Spring Boot 3.3.5 is the required monolith framework.",
            "Spring Boot 3.3.5 is the required monolith framework.",
            5, 30, // Wrong character offsets
            "CLAUDE", "test-model",
            ValidationState.UNVERIFIED, null, "PROPOSED", 1.0,
            null, null, Instant.now()
        );

        MeaningPreservationValidator.ValidationResult result = validator.validate(content, badOffsetFact);
        assertEquals(ValidationState.UNVERIFIED, result.state());
        assertTrue(result.notes().contains("mismatch"));
    }

    @Test
    @DisplayName("Rule 5: Extracted decisions can NEVER be APPROVED; always PROPOSED")
    void testExtractedDecisionsCannotBeApproved() {
        String content = "We decided to adopt Flyway for database migrations.";

        List<ExtractedFact> facts = extractor.extractFacts(
            projectId, artifactId, 1, content, "ANTIGRAVITY", "rule-based-v1"
        );

        assertFalse(facts.isEmpty());
        for (ExtractedFact fact : facts) {
            assertEquals("PROPOSED", fact.approvalState(), "AI extraction output must NEVER be stored as APPROVED");
        }
    }
}

