package com.polaris.ai.safety.dto;

import com.polaris.ai.safety.model.RuleType;

/** Metadata for one matched rule; it deliberately omits submitted text. */
public record ModerationMatch(
        Long ruleId,
        RuleType ruleType,
        String normalizedHash,
        String category,
        Integer weight) {
}
