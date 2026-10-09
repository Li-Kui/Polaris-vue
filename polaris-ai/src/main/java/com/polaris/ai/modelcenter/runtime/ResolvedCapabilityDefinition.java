package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.modelcenter.schema.CapabilitySchemaDefinition;

import java.util.Objects;

/** 已完成可信 Schema 合并和默认值规范化的单项 Capability 定义。 */
public record ResolvedCapabilityDefinition(
        SchemaReference schemaReference,
        CapabilitySchemaDefinition schema,
        ObjectNode parameters,
        String appliesToCapabilityCode) {

    public ResolvedCapabilityDefinition {
        schemaReference = Objects.requireNonNull(
                schemaReference, "schemaReference");
        schema = Objects.requireNonNull(schema, "schema");
        parameters = parameters == null
                ? JsonNodeFactory.instance.objectNode()
                : parameters.deepCopy();
        appliesToCapabilityCode = appliesToCapabilityCode == null
                ? "" : appliesToCapabilityCode;
    }

    @Override
    public ObjectNode parameters() {
        return parameters.deepCopy();
    }
}
