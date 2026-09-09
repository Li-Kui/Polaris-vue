package com.polaris.ai.safety.rule;

import com.polaris.ai.safety.dto.ModerationMatch;
import com.polaris.ai.safety.dto.ModerationMatchSpan;
import com.polaris.ai.safety.model.LocalDecision;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.model.RuleType;
import com.polaris.ai.safety.rule.DictionarySnapshot.SnapshotRule;
import com.polaris.ai.safety.rule.LocalSensitiveMatcher.CandidateMatch;

import java.util.*;

/** Scores recalled candidates using sentence scope and a forty-character context window. */
public final class ContextRiskScorer {
    private static final int CONTEXT_WINDOW = 40;
    private final LocalSensitiveMatcher matcher;

    public ContextRiskScorer() {
        this(new LocalSensitiveMatcher());
    }

    ContextRiskScorer(LocalSensitiveMatcher matcher) {
        this.matcher = matcher;
    }

    public LocalScore score(String raw, DictionarySnapshot snapshot, ModerationPolicy policy) {
        if (raw == null || snapshot == null || policy == null
                || policy.getSuspectThreshold() == null || policy.getBlockThreshold() == null) {
            throw new IllegalArgumentException("Text, snapshot, and policy thresholds are required");
        }
        NormalizedText normalized = snapshot.normalizer().normalize(raw);
        NormalizedText matchingNormalized = snapshot.normalizeForMatching(raw);
        List<Sentence> sentences = sentences(normalized.text());
        List<CandidateMatch> recalled = matcher.findAll(raw, snapshot);

        List<CandidateMatch> applicable = new ArrayList<>();
        for (CandidateMatch candidate : recalled) {
            RuleType type = candidate.rule().ruleType();
            if (type == RuleType.RISK_WORD || hasNearbyRiskAnchor(candidate, recalled, sentences)) {
                applicable.add(candidate);
            }
        }

        Map<String, RiskEvidence> evidenceByKey = new LinkedHashMap<>();
        for (CandidateMatch candidate : applicable) {
            SnapshotRule rule = candidate.rule();
            int sentence = sentenceIndex(candidate.normalizedStart(), sentences);
            String key = sentence + ":" + rule.id();
            evidenceByKey.computeIfAbsent(key,
                    ignored -> new RiskEvidence(candidate, sentence)).add(candidate);
        }
        List<RiskEvidence> grossActive = evidenceByKey.values().stream()
                .filter(evidence -> evidence.remainingWeight() > 0)
                .toList();
        long grossWeight = totalWeight(grossActive);
        Set<String> countedSafeRules = new LinkedHashSet<>();
        for (SafeOccurrence safe : safeOccurrences(normalized.text(), snapshot, sentences)) {
            String countKey = safe.sentenceIndex() + ":SAFE:" + safe.rule().id();
            if (hasCompatibleNearbyRisk(safe, applicable, sentences)
                    && countedSafeRules.add(countKey)) {
                int reduction = -Math.min(0,
                        safe.rule().weight() == null ? 0 : safe.rule().weight());
                reduceActiveEvidence(safe, evidenceByKey.values(), reduction, sentences);
            }
        }
        List<RiskEvidence> reducedActive = evidenceByKey.values().stream()
                .filter(evidence -> evidence.remainingWeight() > 0)
                .toList();
        long reducedWeight = totalWeight(reducedActive);
        boolean preserveGrossEvidence = reducedWeight < grossWeight
                && hasProtectedRiskStructure(grossActive, grossWeight, policy);
        List<RiskEvidence> active = preserveGrossEvidence
                ? grossActive : reducedActive;
        int score = (int) Math.min(100, reducedWeight);
        if (preserveGrossEvidence && score < policy.getSuspectThreshold()) {
            score = policy.getSuspectThreshold();
        }
        LocalDecision decision = decision(score, policy);
        Set<String> categories = active.stream()
                .map(evidence -> evidence.rule().category())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ModerationMatch> matches = active.stream()
                .map(RiskEvidence::rule)
                .distinct()
                .map(rule -> new ModerationMatch(
                        rule.id(), rule.ruleType(), rule.normalizedHash(),
                        rule.category(), rule.weight()))
                .sorted(Comparator.comparing(ModerationMatch::ruleId))
                .toList();
        List<ModerationMatchSpan> matchSpans = active.stream()
                .map(RiskEvidence::representative)
                .map(candidate -> matchSpan(candidate, matchingNormalized))
                .sorted(Comparator.comparingInt(ModerationMatchSpan::originalStart)
                        .thenComparingInt(ModerationMatchSpan::originalEnd)
                        .thenComparing(ModerationMatchSpan::ruleId))
                .toList();
        return new LocalScore(decision, score, categories, matches, matchSpans);
    }

    private static long totalWeight(List<RiskEvidence> evidence) {
        return evidence.stream().mapToLong(RiskEvidence::remainingWeight).sum();
    }

    private static boolean hasProtectedRiskStructure(
            List<RiskEvidence> evidence, long grossWeight,
            ModerationPolicy policy) {
        boolean hasRiskAnchor = evidence.stream().anyMatch(item ->
                item.rule().ruleType() == RuleType.RISK_WORD);
        List<RiskEvidence> contexts = evidence.stream().filter(item ->
                item.rule().ruleType() == RuleType.RISK_CONTEXT).toList();
        long contextWeight = contexts.stream()
                .map(RiskEvidence::rule)
                .map(SnapshotRule::weight)
                .filter(java.util.Objects::nonNull)
                .mapToLong(weight -> Math.max(0, weight))
                .sum();
        return hasRiskAnchor && !contexts.isEmpty()
                && (grossWeight >= policy.getBlockThreshold()
                || contexts.size() >= 2
                || contextWeight >= policy.getSuspectThreshold());
    }

    private static ModerationMatchSpan matchSpan(CandidateMatch candidate,
                                                  NormalizedText matchingNormalized) {
        int normalizedStart = -1;
        int normalizedEnd = -1;
        for (int index = 0; index < matchingNormalized.text().length(); index++) {
            boolean overlaps = matchingNormalized.originalStart(index) < candidate.originalEnd()
                    && matchingNormalized.originalEnd(index) > candidate.originalStart();
            if (overlaps) {
                if (normalizedStart < 0) {
                    normalizedStart = index;
                }
                normalizedEnd = index + 1;
            }
        }
        if (normalizedStart < 0
                || matchingNormalized.originalStartForRange(normalizedStart)
                != candidate.originalStart()
                || matchingNormalized.originalEndForRange(normalizedEnd)
                != candidate.originalEnd()) {
            throw new IllegalStateException(
                    "A recalled moderation occurrence has inconsistent normalized coordinates");
        }
        return new ModerationMatchSpan(
                candidate.rule().id(), candidate.rule().ruleType(),
                candidate.rule().normalizedHash(), candidate.rule().category(),
                candidate.originalStart(), candidate.originalEnd(),
                normalizedStart, normalizedEnd);
    }

    private static void reduceActiveEvidence(SafeOccurrence safe,
                                             Iterable<RiskEvidence> evidence,
                                             int reduction,
                                             List<Sentence> sentences) {
        int remainingReduction = reduction;
        for (RiskEvidence risk : evidence) {
            if (remainingReduction == 0) {
                return;
            }
            if (risk.isCompatibleAndNearby(safe, sentences)) {
                remainingReduction -= risk.reduce(remainingReduction);
            }
        }
    }

    private static boolean hasNearbyRiskAnchor(CandidateMatch context,
                                               List<CandidateMatch> candidates,
                                               List<Sentence> sentences) {
        int sentence = sentenceIndex(context.normalizedStart(), sentences);
        return candidates.stream().anyMatch(anchor ->
                RuleType.RISK_WORD == anchor.rule().ruleType()
                        && sentenceIndex(anchor.normalizedStart(), sentences) == sentence
                        && distance(context.normalizedStart(), context.normalizedEnd(),
                        anchor.normalizedStart(), anchor.normalizedEnd()) <= CONTEXT_WINDOW);
    }

    private static boolean hasCompatibleNearbyRisk(SafeOccurrence safe,
                                                   List<CandidateMatch> candidates,
                                                   List<Sentence> sentences) {
        return candidates.stream().anyMatch(candidate ->
                safe.sentenceIndex() == sentenceIndex(candidate.normalizedStart(), sentences)
                        && compatibleCategory(safe.rule().category(), candidate.rule().category())
                        && distance(safe.start(), safe.end(), candidate.normalizedStart(),
                        candidate.normalizedEnd()) <= CONTEXT_WINDOW);
    }

    private static boolean compatibleCategory(String safeCategory, String riskCategory) {
        return "GENERAL".equals(safeCategory) || safeCategory.equals(riskCategory);
    }

    private static List<SafeOccurrence> safeOccurrences(String text,
                                                        DictionarySnapshot snapshot,
                                                        List<Sentence> sentences) {
        List<SafeOccurrence> occurrences = new ArrayList<>();
        for (SnapshotRule rule : snapshot.safeRules()) {
            String term = snapshot.normalizedTerm(rule);
            int from = 0;
            while (from <= text.length() - term.length()) {
                int start = text.indexOf(term, from);
                if (start < 0) {
                    break;
                }
                occurrences.add(new SafeOccurrence(
                        rule, start, start + term.length(), sentenceIndex(start, sentences)));
                from = start + Math.max(1, term.length());
            }
        }
        return occurrences;
    }

    private static List<Sentence> sentences(String text) {
        List<Sentence> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            int end = index + Character.charCount(codePoint);
            if (isSentenceBoundary(codePoint)) {
                result.add(new Sentence(start, end));
                start = end;
            }
            index = end;
        }
        if (start < text.length() || result.isEmpty()) {
            result.add(new Sentence(start, text.length()));
        }
        return result;
    }

    private static int sentenceIndex(int offset, List<Sentence> sentences) {
        for (int i = 0; i < sentences.size(); i++) {
            if (offset >= sentences.get(i).start() && offset < sentences.get(i).end()) {
                return i;
            }
        }
        return Math.max(0, sentences.size() - 1);
    }

    private static int distance(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        if (firstEnd < secondStart) {
            return secondStart - firstEnd;
        }
        if (secondEnd < firstStart) {
            return firstStart - secondEnd;
        }
        return 0;
    }

    private static boolean isSentenceBoundary(int codePoint) {
        return codePoint == '。' || codePoint == '！' || codePoint == '？'
                || codePoint == '!' || codePoint == '?' || codePoint == '.'
                || codePoint == ';' || codePoint == '；'
                || codePoint == '\n' || codePoint == '\r';
    }

    private static LocalDecision decision(int score, ModerationPolicy policy) {
        if (score >= policy.getBlockThreshold()) {
            return LocalDecision.HIGH_RISK;
        }
        if (score >= policy.getSuspectThreshold()) {
            return LocalDecision.SUSPECT;
        }
        return LocalDecision.PASS;
    }

    private record Sentence(int start, int end) {
    }

    private record SafeOccurrence(
            SnapshotRule rule, int start, int end, int sentenceIndex) {
    }

    private static final class RiskEvidence {
        private final CandidateMatch representative;
        private final int sentenceIndex;
        private final List<CandidateMatch> occurrences = new ArrayList<>();
        private int remainingWeight;

        private RiskEvidence(CandidateMatch representative, int sentenceIndex) {
            this.representative = representative;
            this.sentenceIndex = sentenceIndex;
            this.remainingWeight = Math.max(0,
                    representative.rule().weight() == null
                            ? 0 : representative.rule().weight());
        }

        private void add(CandidateMatch occurrence) {
            occurrences.add(occurrence);
        }

        private SnapshotRule rule() {
            return representative.rule();
        }

        private CandidateMatch representative() {
            return representative;
        }

        private int remainingWeight() {
            return remainingWeight;
        }

        private int reduce(int maximum) {
            int applied = Math.min(remainingWeight, maximum);
            remainingWeight -= applied;
            return applied;
        }

        private boolean isCompatibleAndNearby(SafeOccurrence safe,
                                              List<Sentence> sentences) {
            return sentenceIndex == safe.sentenceIndex()
                    && compatibleCategory(safe.rule().category(), rule().category())
                    && occurrences.stream().anyMatch(candidate ->
                    sentenceIndex(candidate.normalizedStart(), sentences) == sentenceIndex
                            && distance(safe.start(), safe.end(), candidate.normalizedStart(),
                            candidate.normalizedEnd()) <= CONTEXT_WINDOW);
        }
    }
}
