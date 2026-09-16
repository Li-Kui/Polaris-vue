package com.polaris.ai.safety.service;

/** Requests an immediate local dictionary-version reconciliation after a missed reload. */
public record DictionaryVersionReconcileRequested(long candidateVersion) {
}
