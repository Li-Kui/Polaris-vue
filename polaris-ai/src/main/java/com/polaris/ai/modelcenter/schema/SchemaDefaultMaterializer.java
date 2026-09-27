package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将 Schema 中已确定的默认值物化到完整配置。 */
@Component
public class SchemaDefaultMaterializer {

    public ObjectNode materialize(SchemaNode schema, JsonNode input) {
        if (schema == null || schema.type() != SchemaNode.ValueType.OBJECT) {
            throw new IllegalArgumentException("Schema 根节点必须是 object");
        }
        if (input != null && !input.isNull() && !input.isObject()) {
            throw new IllegalArgumentException("Capability config 必须是 object");
        }
        ObjectNode source = input == null || input.isNull()
                ? JsonNodeFactory.instance.objectNode() : (ObjectNode) input;
        return materializeObject(schema, source);
    }

    private ObjectNode materializeObject(SchemaNode schema, ObjectNode source) {
        ObjectNode result = source.deepCopy();
        for (Map.Entry<String, SchemaNode> field : schema.properties().entrySet()) {
            String name = field.getKey();
            SchemaNode fieldSchema = field.getValue();
            if (!result.has(name)) {
                JsonNode defaultValue = fieldSchema.defaultValue();
                if (defaultValue != null) {
                    result.set(name, defaultValue);
                }
                continue;
            }
            JsonNode current = result.get(name);
            if (current != null && current.isObject()
                    && fieldSchema.type() == SchemaNode.ValueType.OBJECT) {
                result.set(name, materializeObject(fieldSchema, (ObjectNode) current));
            }
        }
        return result;
    }
}
