package com.polaris.ai.modelcenter.runtime;

/** Provider 调用的显式重试安全声明。 */
public enum ProviderRetrySafety {
    IDEMPOTENT,
    IDEMPOTENCY_KEY,
    UNSAFE,
    STREAMING;

    public boolean retryAllowed() {
        return this == IDEMPOTENT || this == IDEMPOTENCY_KEY;
    }
}
