package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Polaris 受控 JSON Schema 子集的节点模型。 */
public record SchemaNode(
        ValueType type,
        String title,
        String description,
        Map<String, SchemaNode> properties,
        List<String> required,
        Boolean additionalProperties,
        @JsonProperty("enum") List<JsonNode> enumValues,
        @JsonProperty("default") JsonNode defaultValue,
        BigDecimal minimum,
        BigDecimal maximum,
        BigDecimal multipleOf,
        Integer minLength,
        Integer maxLength,
        SchemaNode items,
        @JsonProperty("$remove") Boolean remove) {

    public SchemaNode {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        required = required == null ? List.of() : List.copyOf(required);
        enumValues = enumValues == null ? List.of() : enumValues.stream()
                .map(SchemaNode::copy)
                .toList();
        defaultValue = defaultValue == null || defaultValue.isNull()
                ? null : copy(defaultValue);
    }

    @Override
    public List<JsonNode> enumValues() {
        return enumValues.stream()
                .map(SchemaNode::copy)
                .toList();
    }

    @Override
    public JsonNode defaultValue() {
        return copy(defaultValue);
    }

    private static JsonNode copy(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }

    public enum ValueType {
        OBJECT,
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        ARRAY;

        @JsonCreator
        public static ValueType fromJson(String value) {
            return value == null ? null
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toJson() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
