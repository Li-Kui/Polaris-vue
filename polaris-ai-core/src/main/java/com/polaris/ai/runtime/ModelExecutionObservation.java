package com.polaris.ai.runtime;

import com.polaris.ai.runtime.usage.NormalizedUsage;

import java.util.Set;

/** 不含 Prompt、Credential、文件或 Base64 正文的执行观测事件。 */
public record ModelExecutionObservation(
        String traceId,
        Long modelId,
        String modelCode,
        long modelRevision,
        Long connectionId,
        long connectionRevision,
        String providerCode,
        String protocolCode,
        String capabilityCode,
        Set<String> activeFeatures,
        long latencyMillis,
        boolean success,
        String errorCategory,
        String providerRequestId,
        NormalizedUsage usage) {

    public ModelExecutionObservation {
        activeFeatures = activeFeatures == null
                ? Set.of() : Set.copyOf(activeFeatures);
        usage = usage == null ? NormalizedUsage.unknown() : usage;
    }
}
