package com.polaris.ai.safety.dto;

import com.polaris.ai.safety.model.RuleType;

import java.util.Objects;

/** Immutable, text-free coordinates for one concrete local-rule occurrence. */
public record ModerationMatchSpan(
        Long ruleId,
        RuleType ruleType,
        String normalizedHash,
        String category,
        int originalStart,
        int originalEnd,
        int normalizedStart,
        int normalizedEnd) {

    public ModerationMatchSpan {
        if (ruleId == null || ruleId <= 0) {
            throw new IllegalArgumentException("A moderation match span requires a valid rule id");
        }
        Objects.requireNonNull(ruleType, "ruleType");
        if (normalizedHash == null || normalizedHash.isBlank()
                || category == null || category.isBlank()) {
            throw new IllegalArgumentException("A moderation match span requires stable metadata");
        }
    }
}
