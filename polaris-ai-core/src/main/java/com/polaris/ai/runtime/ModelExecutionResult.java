package com.polaris.ai.runtime;

import com.polaris.ai.runtime.usage.NormalizedUsage;

/** 非流式 Capability 的统一结果信封。 */
public record ModelExecutionResult<R>(
        R value,
        NormalizedUsage usage,
        String providerRequestId) {

    public ModelExecutionResult {
        usage = usage == null ? NormalizedUsage.unknown() : usage;
    }
}
