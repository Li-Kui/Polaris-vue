package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Runtime Schema 的确定性 canonical JSON 与 SHA-256 计算器。 */
@Component
public class SchemaHasher {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public String hash(CapabilitySchemaDefinition definition) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    canonicalRuntimeJson(definition).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Runtime Schema Hash 计算失败", e);
        }
    }

    public String canonicalRuntimeJson(CapabilitySchemaDefinition definition) {
        ObjectNode runtime = JsonNodeFactory.instance.objectNode();
        runtime.put("code", definition.code());
        runtime.put("kind", definition.kind().name());
        runtime.put("category", definition.category());
        runtime.set("schema", runtimeSchema(definition.schema()));
        runtime.set("conditionRules", objectMapper.valueToTree(definition.conditionRules()));
        runtime.set("parameterPolicy", runtimePolicy(
                definition.schema(), definition.parameterPolicy()));

        ArrayNode targets = runtime.putArray("allowedAppliesTo");
        definition.allowedAppliesTo().stream().sorted().forEach(targets::add);
        return canonicalize(runtime);
    }

    public String canonicalize(JsonNode value) {
        StringBuilder result = new StringBuilder();
        appendCanonical(value, result);
        return result.toString();
    }

    private JsonNode runtimeSchema(SchemaNode schema) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("type", schema.type().toJson());
        if (schema.type() == SchemaNode.ValueType.OBJECT) {
            result.put("additionalProperties", schema.additionalProperties());
            ObjectNode properties = result.putObject("properties");
            schema.properties().forEach((field, child) ->
                    properties.set(field, runtimeSchema(child)));
            ArrayNode required = result.putArray("required");
            schema.required().forEach(required::add);
        }
        if (schema.type() == SchemaNode.ValueType.ARRAY) {
            result.set("items", runtimeSchema(schema.items()));
        }
        if (!schema.enumValues().isEmpty()) {
            ArrayNode values = result.putArray("enum");
            schema.enumValues().forEach(values::add);
        }
        if (schema.defaultValue() != null) {
            result.set("default", schema.defaultValue());
        }
        putNumber(result, "minimum", schema.minimum());
        putNumber(result, "maximum", schema.maximum());
        putNumber(result, "multipleOf", schema.multipleOf());
        if (schema.minLength() != null) {
            result.put("minLength", schema.minLength());
        }
        if (schema.maxLength() != null) {
            result.put("maxLength", schema.maxLength());
        }
        return result;
    }

    private JsonNode runtimePolicy(
            SchemaNode schema,
            Map<String, ParameterPolicyDefinition> policies) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        new java.util.TreeSet<>(schema.properties().keySet()).forEach(field -> {
            ParameterPolicyDefinition policy = policies.getOrDefault(
                    field, new ParameterPolicyDefinition(false, java.util.Set.of()));
            ObjectNode item = result.putObject(field);
            item.put("overridable", policy.overridable());
            ArrayNode sources = item.putArray("allowedSources");
            policy.allowedSources().stream()
                    .map(Enum::name)
                    .sorted()
                    .forEach(sources::add);
        });
        return result;
    }

    private void putNumber(ObjectNode target, String field, BigDecimal value) {
        if (value != null) {
            target.put(field, value);
        }
    }

    private void appendCanonical(JsonNode value, StringBuilder output) {
        if (value == null || value.isNull()) {
            output.append("null");
        } else if (value.isObject()) {
            appendObject(value, output);
        } else if (value.isArray()) {
            appendArray(value, output);
        } else if (value.isNumber()) {
            output.append(normalizeNumber(value.decimalValue()));
        } else if (value.isTextual()) {
            appendJsonString(value.textValue(), output);
        } else if (value.isBoolean()) {
            output.append(value.booleanValue());
        } else {
            throw new IllegalStateException("Canonical JSON 不支持节点: " + value);
        }
    }

    private void appendObject(JsonNode value, StringBuilder output) {
        output.append('{');
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        fields.addAll(value.properties());
        fields.sort(Comparator.comparing(Map.Entry::getKey));
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            Map.Entry<String, JsonNode> field = fields.get(index);
            appendJsonString(field.getKey(), output);
            output.append(':');
            appendCanonical(field.getValue(), output);
        }
        output.append('}');
    }

    private void appendArray(JsonNode value, StringBuilder output) {
        output.append('[');
        for (int index = 0; index < value.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            appendCanonical(value.get(index), output);
        }
        output.append(']');
    }

    private String normalizeNumber(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private void appendJsonString(String value, StringBuilder output) {
        try {
            output.append(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new IllegalStateException("Canonical JSON 字符串编码失败", e);
        }
    }
}
