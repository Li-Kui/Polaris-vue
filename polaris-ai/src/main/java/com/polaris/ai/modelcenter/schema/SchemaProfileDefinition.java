package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 版本化、只读的 Protocol / Provider / Model Schema Profile。 */
public record SchemaProfileDefinition(
        ProfileLayer layer,
        String code,
        int profileVersion,
        String providerCode,
        String modelNamePattern,
        boolean allowConstraintRelaxation,
        String protocolCode,
        String defaultBaseUrl,
        boolean modelDiscoverySupported,
        List<String> supportedCapabilities,
        SchemaNode credentialSchema,
        SchemaNode connectionConfigSchema,
        Map<String, JsonNode> capabilityOverlays) {

    public SchemaProfileDefinition {
        supportedCapabilities = supportedCapabilities == null
                ? List.of()
                : supportedCapabilities.stream()
                .map(SchemaProfileDefinition::normalizeCode)
                .distinct()
                .sorted()
                .toList();
        Map<String, JsonNode> copied = new LinkedHashMap<>();
        if (capabilityOverlays != null) {
            capabilityOverlays.forEach((capability, overlay) ->
                    copied.put(normalizeCode(capability),
                            overlay == null ? null : overlay.deepCopy()));
        }
        capabilityOverlays = Map.copyOf(copied);
    }

    @Override
    public Map<String, JsonNode> capabilityOverlays() {
        Map<String, JsonNode> copied = new LinkedHashMap<>();
        capabilityOverlays.forEach((capability, overlay) ->
                copied.put(capability, overlay.deepCopy()));
        return Map.copyOf(copied);
    }

    public JsonNode overlay(String capabilityCode) {
        JsonNode overlay = capabilityOverlays.get(normalize(capabilityCode));
        return overlay == null ? null : overlay.deepCopy();
    }

    private String normalize(String value) {
        return normalizeCode(value);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public enum ProfileLayer {
        PROTOCOL,
        PROVIDER,
        MODEL;

        @JsonCreator
        public static ProfileLayer fromJson(String value) {
            return value == null ? null
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toJson() {
            return name();
        }
    }
}
