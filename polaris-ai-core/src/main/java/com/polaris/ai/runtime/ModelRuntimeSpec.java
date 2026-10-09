package com.polaris.ai.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 请求级纯运行时 DTO；不依赖数据库 Entity。 */
public record ModelRuntimeSpec(
        Long modelId,
        String modelCode,
        long modelRevision,
        Long connectionId,
        long connectionRevision,
        String providerCode,
        String protocolCode,
        String networkMode,
        String baseUrl,
        String modelName,
        String capabilityCode,
        Map<String, Object> invocationParameters,
        Map<String, Map<String, Object>> featureParameters,
        Set<String> activeFeatures,
        Map<String, Object> providerExtraConfig,
        Map<String, Object> credentials,
        RuntimePolicySpec runtimePolicy,
        int schemaVersion,
        String schemaHash,
        String runtimeDefinitionHash) {

    public ModelRuntimeSpec {
        modelId = Objects.requireNonNull(modelId, "modelId");
        connectionId = Objects.requireNonNull(connectionId, "connectionId");
        capabilityCode = Objects.requireNonNull(
                capabilityCode, "capabilityCode");
        runtimePolicy = Objects.requireNonNull(runtimePolicy, "runtimePolicy");
        invocationParameters = RuntimeValueCopies.map(invocationParameters);
        featureParameters = RuntimeValueCopies.nestedMap(featureParameters);
        activeFeatures = activeFeatures == null
                ? Set.of() : Set.copyOf(activeFeatures);
        providerExtraConfig = RuntimeValueCopies.map(providerExtraConfig);
        credentials = RuntimeValueCopies.map(credentials);
    }

    @Override
    public Map<String, Object> invocationParameters() {
        return RuntimeValueCopies.map(invocationParameters);
    }

    @Override
    public Map<String, Map<String, Object>> featureParameters() {
        return RuntimeValueCopies.nestedMap(featureParameters);
    }

    @Override
    public Map<String, Object> providerExtraConfig() {
        return RuntimeValueCopies.map(providerExtraConfig);
    }

    @Override
    public Map<String, Object> credentials() {
        return RuntimeValueCopies.map(credentials);
    }

    @Override
    public String toString() {
        return "ModelRuntimeSpec[modelId=" + modelId
                + ", modelCode=" + modelCode
                + ", modelRevision=" + modelRevision
                + ", connectionId=" + connectionId
                + ", connectionRevision=" + connectionRevision
                + ", providerCode=" + providerCode
                + ", protocolCode=" + protocolCode
                + ", modelName=" + modelName
                + ", capabilityCode=" + capabilityCode
                + ", activeFeatures=" + activeFeatures
                + ", credentials=<redacted>"
                + ", runtimeDefinitionHash=" + runtimeDefinitionHash + "]";
    }
}
