package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** 固定按 Capability→Protocol→Provider→Model 顺序解析 Schema。 */
@Component
public class CapabilitySchemaResolver {

    private final SchemaMergeEngine mergeEngine;
    private final SchemaHasher schemaHasher;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
    private final CapabilitySchemaDefinitionValidator validator =
            new CapabilitySchemaDefinitionValidator();

    public CapabilitySchemaResolver(
            SchemaMergeEngine mergeEngine,
            SchemaHasher schemaHasher) {
        this.mergeEngine = mergeEngine;
        this.schemaHasher = schemaHasher;
    }

    public ResolvedCapabilitySchema resolve(
            CapabilitySchemaDefinition capabilityBase,
            JsonNode protocolOverlay,
            JsonNode providerOverlay,
            JsonNode modelOverlay) {
        try {
            JsonNode base = objectMapper.valueToTree(capabilityBase);
            JsonNode merged = mergeEngine.merge(
                    base, protocolOverlay, providerOverlay, modelOverlay);
            CapabilitySchemaDefinition definition = objectMapper.treeToValue(
                    merged, CapabilitySchemaDefinition.class);
            validateStableIdentity(capabilityBase, definition);
            validator.validate(definition);
            return new ResolvedCapabilitySchema(
                    definition, schemaHasher.hash(definition));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Resolved Capability Schema 编译失败", e);
        }
    }

    private void validateStableIdentity(
            CapabilitySchemaDefinition base,
            CapabilitySchemaDefinition resolved) {
        if (!base.code().equals(resolved.code())
                || base.kind() != resolved.kind()
                || base.schemaVersion() != resolved.schemaVersion()
                || !base.allowedAppliesTo().equals(resolved.allowedAppliesTo())) {
            throw new IllegalStateException(
                    "Schema Overlay 不能改变 code/kind/schemaVersion/allowedAppliesTo");
        }
    }
}
