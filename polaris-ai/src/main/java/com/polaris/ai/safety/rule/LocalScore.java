package com.polaris.ai.safety.rule;

import com.polaris.ai.safety.dto.ModerationMatch;
import com.polaris.ai.safety.dto.ModerationMatchSpan;
import com.polaris.ai.safety.model.LocalDecision;

import java.util.List;
import java.util.Set;

/** Deterministic local moderation outcome. */
public record LocalScore(
        LocalDecision decision,
        int score,
        Set<String> categories,
        List<ModerationMatch> matches,
        List<ModerationMatchSpan> matchSpans) {

    public LocalScore {
        categories = Set.copyOf(categories);
        matches = List.copyOf(matches);
        matchSpans = List.copyOf(matchSpans);
    }

    public LocalScore(LocalDecision decision, int score,
                      Set<String> categories, List<ModerationMatch> matches) {
        this(decision, score, categories, matches, List.of());
    }
}
