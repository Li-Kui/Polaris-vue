package com.polaris.ai.safety.model;

/** Normalized conclusion from an optional external moderation provider. */
public enum ProviderDecision {
    PASS,
    SUSPECT,
    HIGH_RISK,
    UNAVAILABLE
}
