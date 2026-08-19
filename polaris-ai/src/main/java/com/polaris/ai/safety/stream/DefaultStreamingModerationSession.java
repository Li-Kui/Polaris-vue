package com.polaris.ai.safety.stream;

import com.polaris.ai.safety.dto.ModerationMatchSpan;
import com.polaris.ai.safety.dto.ModerationResult;
import com.polaris.ai.safety.exception.ModerationBlockedException;
import com.polaris.ai.safety.model.LocalDecision;
import com.polaris.ai.safety.model.ModerationPolicy;
import com.polaris.ai.safety.rule.DictionarySnapshot;
import com.polaris.ai.safety.rule.LocalScore;
import com.polaris.ai.safety.rule.NormalizedText;
import com.polaris.ai.safety.rule.TextNormalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/** Bounded release gate that retains a matcher-normalized, never-emitted holdback. */
public final class DefaultStreamingModerationSession implements StreamingModerationSession {
    private static final int DEFAULT_FLUSH_CHARS = 200;
    private static final int MAX_UNFLUSHED_CHARS = 300;

    private final DictionarySnapshot snapshot;
    private final Function<String, ModerationResult> segmentModerator;
    private final Function<String, ModerationResult> completeLocalModerator;
    private final Function<String, LocalScore> localScorer;
    private final int flushChars;
    private final int maxUnflushedChars;
    private final int effectiveOverlap;
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder approved = new StringBuilder();
    private final Set<ReviewedSpan> reviewedCrossBoundarySpans = new LinkedHashSet<>();
    private long pendingOriginChars;
    private int moderatedPrefixChars;
    private boolean blocked;
    private boolean finished;

    public DefaultStreamingModerationSession(
            ModerationPolicy policy,
            DictionarySnapshot snapshot,
            Function<String, ModerationResult> segmentModerator,
            Function<String, ModerationResult> completeLocalModerator,
            Function<String, LocalScore> localScorer) {
        Objects.requireNonNull(policy, "policy");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.segmentModerator = Objects.requireNonNull(segmentModerator, "segmentModerator");
        this.completeLocalModerator = Objects.requireNonNull(
                completeLocalModerator, "completeLocalModerator");
        this.localScorer = Objects.requireNonNull(localScorer, "localScorer");
        this.flushChars = Math.min(DEFAULT_FLUSH_CHARS, positive(
                policy.getSegmentChars(), DEFAULT_FLUSH_CHARS));
        this.maxUnflushedChars = Math.min(MAX_UNFLUSHED_CHARS, positive(
                policy.getOutputBufferChars(), MAX_UNFLUSHED_CHARS));
        this.effectiveOverlap = Math.min(DictionarySnapshot.MAX_STREAM_RISK_TERM_LENGTH,
                Math.max(positive(policy.getSegmentOverlapChars(), 1),
                        snapshot.maxRiskTermLength()));
        if (effectiveOverlap >= maxUnflushedChars) {
            throw new IllegalArgumentException(
                    "Streaming overlap must be smaller than the maximum unflushed content");
        }
    }

    @Override
    public synchronized List<String> append(String chunk) {
        requireWritable();
        if (chunk == null) {
            throw new IllegalArgumentException("Streaming chunk is required");
        }
        if (chunk.isEmpty()) {
            return List.of();
        }
        List<String> released = new ArrayList<>();
        int offset = 0;
        while (offset < chunk.length()) {
            if (pending.length() == maxUnflushedChars) {
                int before = pending.length();
                drainReadySegments(released);
                if (pending.length() == before) {
                    block();
                }
            }
            int capacity = maxUnflushedChars - pending.length();
            int end = Math.min(chunk.length(), offset + capacity);
            if (end < chunk.length() && end > offset
                    && Character.isHighSurrogate(chunk.charAt(end - 1))
                    && Character.isLowSurrogate(chunk.charAt(end))) {
                end--;
            }
            if (end == offset) {
                drainReadySegments(released);
                if (maxUnflushedChars - pending.length() < 2) {
                    block();
                }
                continue;
            }
            pending.append(chunk, offset, end);
            offset = end;
            drainReadySegments(released);
            if (pending.length() == maxUnflushedChars) {
                block();
            }
        }
        return List.copyOf(released);
    }

    @Override
    public synchronized List<String> finish() {
        requireNotBlocked();
        if (finished) {
            return List.of();
        }
        if (moderatedPrefixChars < pending.length()) {
            reviewBoundary(pending.length());
        }
        String complete = approved.toString() + pending;
        if (!complete.isBlank()) {
            approve(completeLocalModerator, complete);
        }
        List<String> released = new ArrayList<>();
        if (!pending.isEmpty()) {
            releasePrefix(pending.length(), released);
        }
        finished = true;
        return List.copyOf(released);
    }

    @Override
    public synchronized String approvedText() {
        return approved.toString();
    }

    @Override
    public synchronized boolean blocked() {
        return blocked;
    }

    private void drainReadySegments(List<String> released) {
        while (true) {
            int boundary = nextBoundary();
            if (boundary < 0) {
                return;
            }
            String candidate = pending.substring(0, boundary);
            reviewBoundary(boundary);
            int releasable = safeReleaseEnd(candidate);
            if (releasable > 0) {
                releasePrefix(releasable, released);
                moderatedPrefixChars = boundary - releasable;
            } else {
                moderatedPrefixChars = boundary;
            }
        }
    }

    /**
     * Reviews only the newly arrived suffix unless the pure local checker proves that the
     * suffix formed a new cross-boundary risk with the retained, already-reviewed prefix.
     * This keeps provider/audit/candidate side effects out of unchanged holdback rechecks.
     */
    private void reviewBoundary(int boundary) {
        if (boundary <= moderatedPrefixChars) {
            return;
        }
        String newSuffix = pending.substring(moderatedPrefixChars, boundary);
        String combined = pending.substring(0, boundary);
        if (moderatedPrefixChars == 0) {
            approve(segmentModerator, newSuffix);
            return;
        }

        LocalScore combinedLocal = analyzeLocal(combined);
        List<ModerationMatchSpan> crossing = crossingSpans(
                combinedLocal, moderatedPrefixChars, combined.length());
        List<ReviewedSpan> unreviewedCrossing = new ArrayList<>();
        for (ModerationMatchSpan span : crossing) {
            ReviewedSpan identity = identity(combined, span);
            if (!reviewedCrossBoundarySpans.contains(identity)) {
                unreviewedCrossing.add(identity);
            }
        }
        if (!unreviewedCrossing.isEmpty()) {
            approve(segmentModerator, combined);
            reviewedCrossBoundarySpans.addAll(unreviewedCrossing);
            return;
        }
        if (combinedLocal.decision() == LocalDecision.HIGH_RISK
                || combinedLocal.decision() != LocalDecision.PASS
                && combinedLocal.matchSpans().isEmpty()) {
            approve(segmentModerator, combined);
            return;
        }
        approve(segmentModerator, newSuffix);
    }

    private LocalScore analyzeLocal(String text) {
        LocalScore result;
        try {
            result = localScorer.apply(text);
        } catch (RuntimeException failure) {
            block();
            return null;
        }
        if (result == null) {
            block();
        }
        return result;
    }

    private List<ModerationMatchSpan> crossingSpans(
            LocalScore combined, int boundary, int combinedLength) {
        NormalizedText normalized = snapshot.normalizeForMatching(
                pending.substring(0, combinedLength));
        for (ModerationMatchSpan span : combined.matchSpans()) {
            if (span.originalStart() < 0 || span.originalEnd() > combinedLength
                    || span.originalStart() >= span.originalEnd()
                    || span.normalizedStart() < 0
                    || span.normalizedEnd() > normalized.text().length()
                    || span.normalizedStart() >= span.normalizedEnd()
                    || normalized.originalStartForRange(span.normalizedStart())
                    != span.originalStart()
                    || normalized.originalEndForRange(span.normalizedEnd())
                    != span.originalEnd()
                    || !hasExactCoverage(span, normalized)) {
                block();
            }
        }
        return combined.matchSpans().stream()
                .filter(span -> span.originalStart() < boundary
                        && span.originalEnd() > boundary)
                .toList();
    }

    private static boolean hasExactCoverage(ModerationMatchSpan span,
                                            NormalizedText normalized) {
        for (int index = 0; index < normalized.text().length(); index++) {
            boolean normalizedInside = index >= span.normalizedStart()
                    && index < span.normalizedEnd();
            boolean originalOverlaps = normalized.originalStart(index) < span.originalEnd()
                    && normalized.originalEnd(index) > span.originalStart();
            if (normalizedInside != originalOverlaps) {
                return false;
            }
        }
        return true;
    }

    private ReviewedSpan identity(String combined, ModerationMatchSpan span) {
        String rawSpan = combined.substring(span.originalStart(), span.originalEnd());
        String normalizedSpan = snapshot.normalizeForMatching(rawSpan).text();
        long absoluteStart = pendingOriginChars + span.originalStart();
        long absoluteEnd = pendingOriginChars + span.originalEnd();
        String stable = span.ruleId() + "\u0000" + span.ruleType() + "\u0000"
                + span.normalizedHash() + "\u0000" + span.category() + "\u0000"
                + absoluteStart + "\u0000" + absoluteEnd + "\u0000" + normalizedSpan;
        return new ReviewedSpan(sha256(stable), absoluteStart, absoluteEnd);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK SHA-256 is unavailable", impossible);
        }
    }

    private int nextBoundary() {
        int sentence = firstSentenceEnd(pending, moderatedPrefixChars);
        int size = moderatedPrefixChars + flushChars <= pending.length()
                ? codePointBoundaryAtOrAfter(pending, moderatedPrefixChars + flushChars)
                : -1;
        int hard = pending.length() >= maxUnflushedChars
                && moderatedPrefixChars < maxUnflushedChars
                ? maxUnflushedChars : -1;
        int boundary = minimumPositive(sentence, size, hard);
        return boundary > moderatedPrefixChars ? boundary : -1;
    }

    private int safeReleaseEnd(String candidate) {
        NormalizedText normalized = snapshot.normalizeForMatching(candidate);
        int normalizedLength = normalized.text().length();
        int overlapReleaseEnd;
        if (normalizedLength == 0) {
            overlapReleaseEnd = candidate.length();
        } else if (normalizedLength <= effectiveOverlap) {
            overlapReleaseEnd = normalized.originalStart(0);
        } else {
            overlapReleaseEnd = normalized.originalStartForRange(
                    normalizedLength - effectiveOverlap);
        }
        int pendingConnectorStart = TextNormalizer.pendingHanConnectorStart(candidate);
        return pendingConnectorStart < 0
                ? overlapReleaseEnd : Math.min(overlapReleaseEnd, pendingConnectorStart);
    }

    private void approve(Function<String, ModerationResult> moderator, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        ModerationResult result = moderator.apply(text);
        if (result == null || !result.isAllowed()) {
            block();
        }
    }

    private void block() {
        blocked = true;
        pending.setLength(0);
        moderatedPrefixChars = 0;
        reviewedCrossBoundarySpans.clear();
        throw new ModerationBlockedException();
    }

    private void releasePrefix(int length, List<String> released) {
        String value = pending.substring(0, length);
        pending.delete(0, length);
        pendingOriginChars += length;
        reviewedCrossBoundarySpans.removeIf(span -> span.end() <= pendingOriginChars);
        if (!value.isEmpty()) {
            approved.append(value);
            released.add(value);
        }
    }

    private void requireWritable() {
        requireNotBlocked();
        if (finished) {
            throw new IllegalStateException("Streaming moderation session is already finished");
        }
    }

    private void requireNotBlocked() {
        if (blocked) {
            throw new ModerationBlockedException();
        }
    }

    private static int firstSentenceEnd(CharSequence text, int start) {
        for (int index = start; index < text.length();) {
            int codePoint = Character.codePointAt(text, index);
            int end = index + Character.charCount(codePoint);
            if (isSentenceBoundary(codePoint)) {
                return end;
            }
            index = end;
        }
        return -1;
    }

    private static int codePointBoundaryAtOrAfter(CharSequence text, int index) {
        if (index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) {
            return index + 1;
        }
        return index;
    }

    private static int minimumPositive(int... values) {
        int minimum = Integer.MAX_VALUE;
        for (int value : values) {
            if (value >= 0) {
                minimum = Math.min(minimum, value);
            }
        }
        return minimum == Integer.MAX_VALUE ? -1 : minimum;
    }

    private static boolean isSentenceBoundary(int codePoint) {
        return codePoint == '。' || codePoint == '！' || codePoint == '？'
                || codePoint == '!' || codePoint == '?' || codePoint == '.'
                || codePoint == ';' || codePoint == '；'
                || codePoint == '\n' || codePoint == '\r';
    }

    private static int positive(Integer configured, int fallback) {
        return configured == null || configured <= 0 ? fallback : configured;
    }

    private record ReviewedSpan(String digest, long start, long end) { }
}
