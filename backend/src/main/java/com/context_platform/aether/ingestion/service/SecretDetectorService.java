package com.context_platform.aether.ingestion.service;

import com.context_platform.aether.ingestion.model.SecretFinding;
import com.context_platform.aether.ingestion.model.SecretFindingType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecretDetectorService {

    public record SecretDetectionResult(
        List<SecretFinding> findings,
        String redactedContent,
        boolean hasSecrets
    ) {}

    private static final List<SecretPatternRule> RULES = List.of(
        new SecretPatternRule(
            SecretFindingType.AWS_KEY,
            Pattern.compile("(?:A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}"),
            "AKIA****************"
        ),
        new SecretPatternRule(
            SecretFindingType.GITHUB_TOKEN,
            Pattern.compile("(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,255}"),
            "ghp_************************************"
        ),
        new SecretPatternRule(
            SecretFindingType.OPENAI_KEY,
            Pattern.compile("sk-[A-Za-z0-9_-]{20,64}"),
            "sk-************************************"
        ),
        new SecretPatternRule(
            SecretFindingType.JWT_TOKEN,
            Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"),
            "eyJ****************.****************.****************"
        ),
        new SecretPatternRule(
            SecretFindingType.PRIVATE_KEY,
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
            "-----BEGIN PRIVATE KEY-----\n[REDACTED PRIVATE KEY]\n-----END PRIVATE KEY-----"
        )
    );

    private static final Pattern HIGH_ENTROPY_CANDIDATE = Pattern.compile("(?i)(?:secret|token|password|api[_-]?key|credential|auth)[\\s:=]+[\"']?([A-Za-z0-9+/=_-]{24,})[\"']?");

    public SecretDetectionResult scanAndRedact(UUID projectId, UUID artifactId, int artifactVersion, String content) {
        if (content == null || content.isEmpty()) {
            return new SecretDetectionResult(List.of(), content, false);
        }

        List<SecretFinding> findings = new ArrayList<>();
        StringBuilder redacted = new StringBuilder(content);
        
        // Track offset replacements to adjust subsequent indices
        List<FindingSpan> detectedSpans = new ArrayList<>();

        // 1. Check known specific pattern rules
        for (SecretPatternRule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(content);
            while (matcher.find()) {
                String match = matcher.group();
                int start = matcher.start();
                int end = matcher.end();
                int line = countLines(content, start);
                double entropy = calculateShannonEntropy(match);

                findings.add(new SecretFinding(
                    UUID.randomUUID(),
                    projectId,
                    artifactId,
                    artifactVersion,
                    rule.type(),
                    maskSnippet(match),
                    line,
                    start,
                    end,
                    entropy,
                    Instant.now()
                ));

                detectedSpans.add(new FindingSpan(start, end, rule.redactionPlaceholder()));
            }
        }

        // 2. Check high entropy candidates near sensitive keywords
        Matcher entropyMatcher = HIGH_ENTROPY_CANDIDATE.matcher(content);
        while (entropyMatcher.find()) {
            String candidate = entropyMatcher.group(1);
            int start = entropyMatcher.start(1);
            int end = entropyMatcher.end(1);
            double entropy = calculateShannonEntropy(candidate);

            if (entropy >= 4.5 && !isAlreadyCovered(detectedSpans, start, end)) {
                int line = countLines(content, start);
                findings.add(new SecretFinding(
                    UUID.randomUUID(),
                    projectId,
                    artifactId,
                    artifactVersion,
                    SecretFindingType.HIGH_ENTROPY,
                    maskSnippet(candidate),
                    line,
                    start,
                    end,
                    entropy,
                    Instant.now()
                ));

                detectedSpans.add(new FindingSpan(start, end, "[REDACTED_HIGH_ENTROPY_SECRET]"));
            }
        }

        // Apply redactions in reverse order to preserve string indices
        detectedSpans.sort(Comparator.comparingInt(FindingSpan::start).reversed());
        for (FindingSpan span : detectedSpans) {
            redacted.replace(span.start(), span.end(), span.replacement());
        }

        return new SecretDetectionResult(findings, redacted.toString(), !findings.isEmpty());
    }

    public static double calculateShannonEntropy(String input) {
        if (input == null || input.isEmpty()) return 0.0;
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : input.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }
        double entropy = 0.0;
        double len = input.length();
        for (int count : freq.values()) {
            double p = count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private static String maskSnippet(String raw) {
        if (raw.length() <= 8) {
            return "********";
        }
        return raw.substring(0, 4) + "****************" + raw.substring(raw.length() - 2);
    }

    private static int countLines(String text, int offset) {
        int lines = 1;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private static boolean isAlreadyCovered(List<FindingSpan> spans, int start, int end) {
        for (FindingSpan span : spans) {
            if (start >= span.start() && end <= span.end()) return true;
        }
        return false;
    }

    private record SecretPatternRule(SecretFindingType type, Pattern pattern, String redactionPlaceholder) {}
    private record FindingSpan(int start, int end, String replacement) {}
}

