package com.polaris.ai.safety.dto;

import com.polaris.ai.safety.model.ProviderDecision;

import java.util.Set;

/** Immutable normalized provider metadata without original submitted content. */
public record ProviderResult(
        String provider,
        ProviderDecision decision,
        Integer riskScore,
        Set<String> categories,
        String providerRequestId,
        Long latencyMs) {

    public ProviderResult {
        categories = categories == null ? Set.of() : Set.copyOf(categories);
    }
}
