package com.polaris.ai.safety.model;

/** Enforcement action after all available decisions have been combined. */
public enum FinalAction {
    ALLOW,
    BLOCK,
    QUARANTINE,
    REPLACE
}
