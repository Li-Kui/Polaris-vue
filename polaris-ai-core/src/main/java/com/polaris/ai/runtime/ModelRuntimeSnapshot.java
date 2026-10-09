package com.polaris.ai.runtime;

import java.util.Map;
import java.util.Objects;

/** 长任务可持久化的非 Secret Runtime Snapshot。 */
public record ModelRuntimeSnapshot(
        Long modelId,
        String modelCode,
        long modelRevision,
        Long connectionId,
        long connectionRevision,
        String providerCode,
        String protocolCode,
        String baseUrl,
        String modelName,
        String capabilityCode,
        Map<String, Object> invocationParameters,
        Map<String, Map<String, Object>> featureParameters,
        RuntimePolicySpec runtimePolicy,
        int schemaVersion,
        String schemaHash) {

    public ModelRuntimeSnapshot {
        modelId = Objects.requireNonNull(modelId, "modelId");
        connectionId = Objects.requireNonNull(connectionId, "connectionId");
        capabilityCode = Objects.requireNonNull(
                capabilityCode, "capabilityCode");
        runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
        invocationParameters = RuntimeValueCopies.map(invocationParameters);
        featureParameters = RuntimeValueCopies.nestedMap(featureParameters);
    }

    @Override
    public Map<String, Object> invocationParameters() {
        return RuntimeValueCopies.map(invocationParameters);
    }

    @Override
    public Map<String, Map<String, Object>> featureParameters() {
        return RuntimeValueCopies.nestedMap(featureParameters);
    }
}
