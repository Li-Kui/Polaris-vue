package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Objects;

/** 可缓存的静态模型定义；不包含 Credential 或任何请求级 Override。 */
public record ResolvedModelDefinition(
        Long modelId,
        String modelCode,
        long modelRevision,
        Long connectionId,
        long connectionRevision,
        String providerCode,
        String protocolCode,
        int protocolAdapterVersion,
        RuntimeProfileVersions profileVersions,
        String networkMode,
        String baseUrl,
        ObjectNode providerExtraConfig,
        String modelName,
        ResolvedCapabilityDefinition invocation,
        Map<String, ResolvedCapabilityDefinition> enabledFeatures,
        RuntimePolicyLayers runtimePolicyLayers,
        String runtimeDefinitionHash) {

    public ResolvedModelDefinition {
        modelId = Objects.requireNonNull(modelId, "modelId");
        connectionId = Objects.requireNonNull(connectionId, "connectionId");
        invocation = Objects.requireNonNull(invocation, "invocation");
        profileVersions = Objects.requireNonNull(
                profileVersions, "profileVersions");
        providerExtraConfig = providerExtraConfig == null
                ? JsonNodeFactory.instance.objectNode()
                : providerExtraConfig.deepCopy();
        enabledFeatures = enabledFeatures == null
                ? Map.of() : Map.copyOf(enabledFeatures);
        runtimePolicyLayers = runtimePolicyLayers == null
                ? new RuntimePolicyLayers(null, null) : runtimePolicyLayers;
    }

    @Override
    public ObjectNode providerExtraConfig() {
        return providerExtraConfig.deepCopy();
    }

    public ResolvedModelDefinition withRuntimeDefinitionHash(String hash) {
        return new ResolvedModelDefinition(
                modelId, modelCode, modelRevision, connectionId,
                connectionRevision, providerCode, protocolCode,
                protocolAdapterVersion, profileVersions, networkMode,
                baseUrl, providerExtraConfig, modelName, invocation,
                enabledFeatures, runtimePolicyLayers, hash);
    }

    @Override
    public String toString() {
        return "ResolvedModelDefinition[modelId=" + modelId
                + ", modelCode=" + modelCode
                + ", modelRevision=" + modelRevision
                + ", connectionId=" + connectionId
                + ", connectionRevision=" + connectionRevision
                + ", providerCode=" + providerCode
                + ", protocolCode=" + protocolCode
                + ", modelName=" + modelName
                + ", capability="
                + invocation.schemaReference().capabilityCode()
                + ", enabledFeatures=" + enabledFeatures.keySet()
                + ", runtimeDefinitionHash=" + runtimeDefinitionHash + "]";
    }
}
