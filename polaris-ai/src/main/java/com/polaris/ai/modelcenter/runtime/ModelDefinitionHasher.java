package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.modelcenter.schema.SchemaHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;

/** 对 Runtime Definition 的非 UI、非 Secret 语义计算确定性 SHA-256。 */
@Component
public class ModelDefinitionHasher {

    private final SchemaHasher schemaHasher;

    public ModelDefinitionHasher(SchemaHasher schemaHasher) {
        this.schemaHasher = schemaHasher;
    }

    public String hash(ResolvedModelDefinition definition) {
        ObjectNode runtime = JsonNodeFactory.instance.objectNode();
        runtime.put("modelId", definition.modelId());
        runtime.put("modelCode", definition.modelCode());
        runtime.put("modelName", definition.modelName());
        runtime.put("connectionId", definition.connectionId());
        runtime.put("providerCode", definition.providerCode());
        runtime.put("protocolCode", definition.protocolCode());
        runtime.put("protocolAdapterVersion",
                definition.protocolAdapterVersion());
        ObjectNode profileVersions = runtime.putObject("profileVersions");
        profileVersions.put("protocol", definition.profileVersions().protocol());
        profileVersions.put("provider", definition.profileVersions().provider());
        if (definition.profileVersions().model() == null) {
            profileVersions.putNull("model");
        } else {
            profileVersions.put("model", definition.profileVersions().model());
        }
        runtime.put("networkMode", definition.networkMode());
        runtime.put("baseUrl", definition.baseUrl());
        runtime.set("providerExtraConfig", definition.providerExtraConfig());
        runtime.set("invocation", capability(definition.invocation()));
        ObjectNode features = runtime.putObject("features");
        definition.enabledFeatures().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> features.set(
                        entry.getKey(), capability(entry.getValue())));
        runtime.set("runtimePolicy", policy(definition.runtimePolicyLayers()));
        return digest(schemaHasher.canonicalize(runtime));
    }

    public String schemaFingerprint(
            java.util.List<SchemaReference> references) {
        ArrayNode schemas = JsonNodeFactory.instance.arrayNode();
        references.stream()
                .sorted(Comparator.comparing(SchemaReference::capabilityCode)
                        .thenComparingInt(SchemaReference::schemaVersion)
                        .thenComparing(SchemaReference::schemaHash))
                .forEach(reference -> schemas.add(schemaReference(reference)));
        return digest(schemaHasher.canonicalize(schemas));
    }

    private ObjectNode capability(ResolvedCapabilityDefinition capability) {
        ObjectNode value = schemaReference(capability.schemaReference());
        value.put("appliesTo", capability.appliesToCapabilityCode());
        value.set("parameters", capability.parameters());
        return value;
    }

    private ObjectNode schemaReference(SchemaReference reference) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("code", reference.capabilityCode());
        value.put("version", reference.schemaVersion());
        value.put("hash", reference.schemaHash());
        return value;
    }

    private ObjectNode policy(RuntimePolicyLayers layers) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.set("model", policyLayer(layers.model()));
        value.set("capability", policyLayer(layers.capability()));
        return value;
    }

    private ObjectNode policyLayer(RuntimePolicyLayer layer) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        put(value, "maxConcurrency", layer.maxConcurrency());
        put(value, "connectTimeoutMs", layer.connectTimeoutMs());
        put(value, "readTimeoutMs", layer.readTimeoutMs());
        put(value, "retryCount", layer.retryCount());
        if (layer.qpsLimit() != null) {
            value.put("qpsLimit", layer.qpsLimit());
        }
        put(value, "priority", layer.priority());
        value.set("extraConfig", layer.extraConfig());
        return value;
    }

    private void put(ObjectNode target, String field, Integer value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private String digest(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    canonicalJson.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Runtime Definition Hash 计算失败", e);
        }
    }
}
