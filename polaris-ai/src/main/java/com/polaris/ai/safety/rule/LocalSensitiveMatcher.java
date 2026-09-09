package com.polaris.ai.safety.rule;

import com.github.houbb.sensitive.word.api.IWordResult;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.result.WordResultHandlers;
import com.polaris.ai.safety.rule.DictionarySnapshot.SnapshotRule;

import java.util.*;

/** Candidate recall backed only by the snapshot's risk rules and ephemeral aliases. */
public final class LocalSensitiveMatcher {
    public List<CandidateMatch> findAll(String raw, DictionarySnapshot snapshot) {
        if (snapshot == null || snapshot.matcher() == null || snapshot.allTerms().isEmpty()) {
            return List.of();
        }
        NormalizedText canonical = snapshot.normalizer().normalize(raw);
        NormalizedText matching = snapshot.normalizeForMatching(raw);
        Map<String, List<SnapshotRule>> allTerms = snapshot.allTerms();

        SensitiveWordBs matcher = snapshot.matcher();
        List<IWordResult> rawMatches = matcher.findAll(
                matching.text(), WordResultHandlers.raw());
        List<CandidateMatch> matches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (IWordResult rawMatch : rawMatches) {
            int start = rawMatch.startIndex();
            int end = rawMatch.endIndex();
            if (start < 0 || end <= start || end > matching.text().length()) {
                continue;
            }
            String matchedTerm = rawMatch.word();
            boolean alias = snapshot.aliasesByTerm().containsKey(matchedTerm);
            if ((alias || isAsciiTerm(matchedTerm))
                    && !hasCompleteAsciiBoundary(matching.text(), start, end)) {
                continue;
            }
            if (isExactlyAllowed(raw, matching, start, end, snapshot)) {
                continue;
            }
            for (SnapshotRule rule : allTerms.getOrDefault(matchedTerm, List.of())) {
                String key = rule.id() + ":" + start + ":" + end;
                if (seen.add(key)) {
                    matches.add(candidate(rule, start, end, alias, matching, canonical));
                }
            }
        }
        return List.copyOf(matches);
    }

    private static CandidateMatch candidate(SnapshotRule rule, int matchingStart,
                                            int matchingEnd, boolean alias,
                                            NormalizedText matching,
                                            NormalizedText canonical) {
        int originalStart = matching.originalStartForRange(matchingStart);
        int originalEnd = matching.originalEndForRange(matchingEnd);
        int canonicalStart = -1;
        int canonicalEnd = -1;
        for (int index = 0; index < canonical.text().length(); index++) {
            if (canonical.originalStart(index) < originalEnd
                    && canonical.originalEnd(index) > originalStart) {
                if (canonicalStart < 0) {
                    canonicalStart = index;
                }
                canonicalEnd = index + 1;
            }
        }
        if (canonicalStart < 0) {
            throw new IllegalStateException(
                    "A matching projection occurrence has no canonical coordinates");
        }
        return new CandidateMatch(rule, canonicalStart, canonicalEnd,
                originalStart, originalEnd, alias);
    }

    private static boolean isExactlyAllowed(String raw, NormalizedText matching,
                                            int start, int end,
                                            DictionarySnapshot snapshot) {
        int originalStart = matching.originalStartForRange(start);
        int originalEnd = matching.originalEndForRange(end);
        String candidateSpan = snapshot.normalizer()
                .normalize(raw.substring(originalStart, originalEnd)).text();
        return snapshot.allowRules().stream()
                .map(snapshot::normalizedTerm)
                .anyMatch(candidateSpan::equals);
    }

    private static boolean hasCompleteAsciiBoundary(String text, int start, int end) {
        return (start == 0 || !isAsciiToken(text.charAt(start - 1)))
                && (end == text.length() || !isAsciiToken(text.charAt(end)));
    }

    private static boolean isAsciiToken(int value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '_';
    }

    private static boolean isAsciiTerm(String term) {
        return !term.isEmpty() && term.chars().allMatch(LocalSensitiveMatcher::isAsciiToken);
    }

    public record CandidateMatch(
            SnapshotRule rule,
            int normalizedStart,
            int normalizedEnd,
            int originalStart,
            int originalEnd,
            boolean alias) {
    }
}
