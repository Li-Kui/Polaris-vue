package com.polaris.ai.modelcenter.audio;

import com.polaris.ai.runtime.usage.NormalizedUsage;

public record AudioProtocolResult<T>(
        T value,
        NormalizedUsage usage,
        String providerRequestId) {
}
