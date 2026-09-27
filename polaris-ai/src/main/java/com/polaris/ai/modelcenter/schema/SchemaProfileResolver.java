package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** 使用 Registry 中的最新可信资源组装四层 Resolved Schema。 */
@Component
public class SchemaProfileResolver {

    private final CapabilitySchemaRegistry capabilityRegistry;
    private final SchemaProfileRegistry profileRegistry;
    private final CapabilitySchemaResolver schemaResolver;
    private final ModelProfileTighteningValidator tighteningValidator;

    public SchemaProfileResolver(
            CapabilitySchemaRegistry capabilityRegistry,
            SchemaProfileRegistry profileRegistry,
            CapabilitySchemaResolver schemaResolver,
            ModelProfileTighteningValidator tighteningValidator) {
        this.capabilityRegistry = capabilityRegistry;
        this.profileRegistry = profileRegistry;
        this.schemaResolver = schemaResolver;
        this.tighteningValidator = tighteningValidator;
    }

    public ResolvedCapabilitySchema resolve(
            String capabilityCode,
            int schemaVersion,
            String protocolCode,
            String providerCode,
            String modelName) {
        CapabilitySchemaDefinition base = capabilityRegistry.getRequired(
                capabilityCode, schemaVersion);
        return resolve(base, protocolCode, providerCode, modelName);
    }

    public ResolvedCapabilitySchema resolveLatest(
            String capabilityCode,
            String protocolCode,
            String providerCode,
            String modelName) {
        CapabilitySchemaDefinition base = capabilityRegistry.getLatestRequired(
                capabilityCode);
        return resolve(base, protocolCode, providerCode, modelName);
    }

    private ResolvedCapabilitySchema resolve(
            CapabilitySchemaDefinition base,
            String protocolCode,
            String providerCode,
            String modelName) {
        SchemaProfileDefinition protocol = profileRegistry.findLatest(
                SchemaProfileDefinition.ProfileLayer.PROTOCOL, protocolCode)
                .orElse(null);
        SchemaProfileDefinition provider = profileRegistry.findLatest(
                SchemaProfileDefinition.ProfileLayer.PROVIDER, providerCode)
                .orElse(null);
        SchemaProfileDefinition model = profileRegistry.matchModel(
                providerCode, modelName).orElse(null);

        JsonNode protocolOverlay = overlay(protocol, base.code());
        JsonNode providerOverlay = overlay(provider, base.code());
        JsonNode modelOverlay = overlay(model, base.code());
        ResolvedCapabilitySchema beforeModel = schemaResolver.resolve(
                base, protocolOverlay, providerOverlay, null);
        ResolvedCapabilitySchema resolved = schemaResolver.resolve(
                base, protocolOverlay, providerOverlay, modelOverlay);
        if (model != null && modelOverlay != null
                && !model.allowConstraintRelaxation()) {
            tighteningValidator.validate(
                    beforeModel.definition(), resolved.definition());
        }
        return resolved;
    }

    private JsonNode overlay(
            SchemaProfileDefinition profile,
            String capabilityCode) {
        return profile == null ? null : profile.overlay(capabilityCode);
    }
}
