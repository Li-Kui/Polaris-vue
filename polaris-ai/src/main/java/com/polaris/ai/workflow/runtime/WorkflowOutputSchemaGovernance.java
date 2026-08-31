package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 用户确认的正式输出 Schema 的限制、摘要和兼容性检查。 */
public final class WorkflowOutputSchemaGovernance {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WorkflowOutputSchemaGovernance() {
    }

    public static List<String> validate(JsonNode schema) {
        return WorkflowStructuredOutput.validateSchema(schema).stream()
                .map(message -> message.replace("结构化输出 Schema", "正式输出 Schema"))
                .toList();
    }

    public static String fingerprint(JsonNode schema) {
        if (schema == null || schema.isNull()) return null;
        try {
            JsonNode canonical = canonicalize(schema);
            byte[] bytes = OBJECT_MAPPER.writeValueAsString(canonical)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算正式输出 Schema 摘要", e);
        }
    }

    public static List<String> conflicts(JsonNode base, JsonNode override) {
        if (base == null || !base.isObject() || base.isEmpty()
                || override == null || !override.isObject()) {
            return List.of();
        }
        List<String> diagnostics = new ArrayList<>();
        compare(base, override, "$", diagnostics);
        return List.copyOf(diagnostics);
    }

    public static boolean containsPath(JsonNode schema, List<String> segments) {
        if (schema == null || !schema.isObject()) return false;
        JsonNode current = schema;
        for (String segment : segments == null ? List.<String>of() : segments) {
            if (segment == null || segment.isBlank()) continue;
            if (declaresType(current.path("type"), "array")) {
                current = current.path("items");
            }
            JsonNode properties = current.path("properties");
            if (properties.isObject() && properties.has(segment)) {
                current = properties.path(segment);
                continue;
            }
            JsonNode additional = current.path("additionalProperties");
            if (additional.isBoolean() && additional.asBoolean()) return true;
            if (additional.isObject()) {
                current = additional;
                continue;
            }
            return false;
        }
        return true;
    }

    private static void compare(
            JsonNode base, JsonNode override, String path,
            List<String> diagnostics) {
        if (diagnostics.size() >= 50) return;
        Set<String> baseTypes = types(base.path("type"));
        Set<String> overrideTypes = types(override.path("type"));
        if (!baseTypes.isEmpty() && !overrideTypes.isEmpty()
                && baseTypes.stream().noneMatch(overrideTypes::contains)) {
            diagnostics.add(path + " 类型由 " + String.join("/", baseTypes)
                    + " 覆盖为 " + String.join("/", overrideTypes));
            return;
        }
        if (baseTypes.contains("object") && overrideTypes.contains("object")) {
            Set<String> baseRequired = textValues(base.path("required"));
            Set<String> overrideRequired = textValues(override.path("required"));
            baseRequired.stream().filter(name -> !overrideRequired.contains(name))
                    .forEach(name -> diagnostics.add(
                            path + "." + name + " 不再是正式必填字段"));
            JsonNode baseProperties = base.path("properties");
            JsonNode overrideProperties = override.path("properties");
            if (baseProperties.isObject() && overrideProperties.isObject()) {
                baseProperties.fields().forEachRemaining(field -> {
                    if (overrideProperties.has(field.getKey())) {
                        compare(field.getValue(), overrideProperties.path(field.getKey()),
                                path + "." + field.getKey(), diagnostics);
                    }
                });
            }
        }
        if (baseTypes.contains("array") && overrideTypes.contains("array")
                && base.path("items").isObject() && override.path("items").isObject()) {
            compare(base.path("items"), override.path("items"), path + "[]", diagnostics);
        }
    }

    private static Set<String> types(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        if (node.isTextual()) result.add(node.asText());
        if (node.isArray()) node.forEach(item -> {
            if (item.isTextual()) result.add(item.asText());
        });
        return result;
    }

    private static Set<String> textValues(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        if (node.isArray()) node.forEach(item -> {
            if (item.isTextual()) result.add(item.asText());
        });
        return result;
    }

    private static boolean declaresType(JsonNode node, String expected) {
        return types(node).contains(expected);
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isValueNode()) return node;
        if (node.isArray()) {
            ArrayNode result = OBJECT_MAPPER.createArrayNode();
            node.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        Map<String, JsonNode> fields = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> field = iterator.next();
            fields.put(field.getKey(), field.getValue());
        }
        fields.forEach((key, value) -> result.set(key, canonicalize(value)));
        return result;
    }
}
