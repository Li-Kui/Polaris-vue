package com.polaris.ai.safety.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.polaris.ai.safety.model.FinalAction;
import com.polaris.ai.safety.model.LocalDecision;

import java.util.List;
import java.util.Set;

/** Immutable moderation outcome. It never exposes the original text. */
@JsonIgnoreProperties(value = "allowed", allowGetters = true)
public record ModerationResult(
        LocalDecision localDecision,
        FinalAction finalAction,
        int riskScore,
        Set<String> categories,
        List<ModerationMatch> matches,
        Long dictionaryVersion,
        Long policyVersion,
        ProviderResult providerResult,
        String fallbackReason) {

    public ModerationResult {
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public boolean isAllowed() {
        return finalAction == FinalAction.ALLOW;
    }
}
