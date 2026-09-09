package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 大模型结构化输出使用的受控 JSON Schema 子集。 */
public final class WorkflowStructuredOutput {

    private static final int MAX_SCHEMA_BYTES = 32 * 1024;
    private static final int MAX_DEPTH = 12;
    private static final int MAX_PROPERTIES = 200;
    private static final int MAX_ENUM_VALUES = 100;
    private static final Set<String> TYPES = Set.of(
            "object", "array", "string", "integer", "number", "boolean", "null");
    private static final Set<String> KEYWORDS = Set.of(
            "$schema", "type", "title", "description", "properties", "required",
            "additionalProperties", "items", "enum", "const", "format", "pattern",
            "minLength", "maxLength", "minItems", "maxItems", "uniqueItems",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum");

    private WorkflowStructuredOutput() {
    }

    public static List<String> validateSchema(JsonNode schema) {
        List<String> errors = new ArrayList<>();
        if (schema == null || schema.isNull() || !schema.isObject()) {
            return List.of("结构化输出 Schema 必须是 JSON 对象");
        }
        if (schema.toString().getBytes(StandardCharsets.UTF_8).length > MAX_SCHEMA_BYTES) {
            errors.add("结构化输出 Schema 不能超过 32KB");
            return errors;
        }
        validateNode(schema, "$", 1, new int[]{0}, errors);
        if (!declaresType(schema.path("type"), "object")) {
            errors.add("结构化输出根类型必须包含 object");
        }
        return List.copyOf(errors);
    }

    public static JsonNode parseAndValidate(
            ObjectMapper objectMapper, WorkflowInputValidator validator,
            String value, JsonNode schema) {
        List<String> schemaErrors = validateSchema(schema);
        if (!schemaErrors.isEmpty()) {
            throw new IllegalStateException("结构化输出 Schema 无效: " + schemaErrors.get(0));
        }
        String normalized = stripMarkdownFence(value);
        JsonNode result;
        try {
            result = objectMapper.readTree(normalized);
        } catch (Exception e) {
            throw new IllegalStateException("大模型结构化输出不是有效 JSON", e);
        }
        if (result == null || !result.isObject()) {
            throw new IllegalStateException("大模型结构化输出必须是 JSON 对象");
        }
        List<String> errors = validator.validate(schema, result, "$.output");
        if (!errors.isEmpty()) {
            int end = Math.min(errors.size(), 5);
            throw new IllegalStateException(
                    "大模型结构化输出不符合 Schema: " + String.join("；", errors.subList(0, end)));
        }
        return result;
    }

    public static String instruction(JsonNode schema) {
        return "只能返回符合以下 JSON Schema 的 JSON 对象，不要输出 Markdown 代码块、解释或其他文本："
                + schema;
    }

    public static String fingerprint(JsonNode schema) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(schema.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算结构化输出 Schema 摘要", e);
        }
    }

    private static void validateNode(
            JsonNode schema, String path, int depth, int[] propertyCount,
            List<String> errors) {
        if (errors.size() >= 50) return;
        if (!schema.isObject()) {
            errors.add(path + " 必须是 Schema 对象");
            return;
        }
        if (depth > MAX_DEPTH) {
            errors.add(path + " 嵌套深度不能超过 " + MAX_DEPTH);
            return;
        }
        Iterator<String> names = schema.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!KEYWORDS.contains(name)) errors.add(path + " 使用了不支持的关键字 " + name);
        }
        Set<String> declaredTypes = validateTypes(schema.path("type"), path, errors);
        validateMetadata(schema, path, errors);
        validateEnum(schema, path, errors);
        if (declaredTypes.contains("object")) {
            validateObject(schema, path, depth, propertyCount, errors);
        }
        if (declaredTypes.contains("array")) {
            JsonNode items = schema.path("items");
            if (!items.isObject()) {
                errors.add(path + ".items 为必填 Schema 对象");
            } else {
                validateNode(items, path + ".items", depth + 1, propertyCount, errors);
            }
        }
    }

    private static Set<String> validateTypes(
            JsonNode typeNode, String path, List<String> errors) {
        Set<String> result = new HashSet<>();
        if (typeNode.isTextual()) {
            result.add(typeNode.asText());
        } else if (typeNode.isArray() && !typeNode.isEmpty()) {
            typeNode.forEach(item -> {
                if (item.isTextual()) result.add(item.asText());
                else errors.add(path + ".type 只能包含字符串类型名");
            });
            if (result.size() != typeNode.size()) {
                errors.add(path + ".type 不能包含重复类型");
            }
        } else {
            errors.add(path + ".type 必须是类型名或非空类型数组");
        }
        result.stream().filter(type -> !TYPES.contains(type))
                .forEach(type -> errors.add(path + ".type 不支持 " + type));
        return result;
    }

    private static void validateObject(
            JsonNode schema, String path, int depth, int[] propertyCount,
            List<String> errors) {
        JsonNode properties = schema.path("properties");
        if (!properties.isObject()) {
            errors.add(path + ".properties 为必填对象");
            return;
        }
        propertyCount[0] += properties.size();
        if (propertyCount[0] > MAX_PROPERTIES) {
            errors.add("结构化输出 Schema 属性总数不能超过 " + MAX_PROPERTIES);
            return;
        }
        properties.fields().forEachRemaining(field -> validateNode(
                field.getValue(), path + ".properties." + field.getKey(),
                depth + 1, propertyCount, errors));
        JsonNode required = schema.path("required");
        if (!required.isMissingNode()) {
            if (!required.isArray()) {
                errors.add(path + ".required 必须是字符串数组");
            } else {
                Set<String> names = new HashSet<>();
                required.forEach(item -> {
                    if (!item.isTextual()) {
                        errors.add(path + ".required 只能包含字段名");
                    } else if (!properties.has(item.asText())) {
                        errors.add(path + ".required 包含未声明字段 " + item.asText());
                    } else if (!names.add(item.asText())) {
                        errors.add(path + ".required 不能包含重复字段 " + item.asText());
                    }
                });
            }
        }
        JsonNode additional = schema.path("additionalProperties");
        if (!additional.isMissingNode() && !additional.isBoolean()) {
            errors.add(path + ".additionalProperties 仅支持布尔值");
        }
    }

    private static void validateMetadata(JsonNode schema, String path, List<String> errors) {
        for (String key : List.of("title", "description", "format", "pattern", "$schema")) {
            if (schema.has(key) && !schema.path(key).isTextual()) {
                errors.add(path + "." + key + " 必须是字符串");
            }
        }
        for (String key : List.of("minLength", "maxLength", "minItems", "maxItems")) {
            if (schema.has(key) && (!schema.path(key).isIntegralNumber()
                    || schema.path(key).asInt() < 0)) {
                errors.add(path + "." + key + " 必须是非负整数");
            }
        }
        for (String key : List.of(
                "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum")) {
            if (schema.has(key) && !schema.path(key).isNumber()) {
                errors.add(path + "." + key + " 必须是数字");
            }
        }
        if (schema.has("uniqueItems") && !schema.path("uniqueItems").isBoolean()) {
            errors.add(path + ".uniqueItems 必须是布尔值");
        }
        if (schema.hasNonNull("pattern")) {
            try {
                Pattern.compile(schema.path("pattern").asText());
            } catch (PatternSyntaxException e) {
                errors.add(path + ".pattern 不是有效正则表达式");
            }
        }
    }

    private static void validateEnum(JsonNode schema, String path, List<String> errors) {
        JsonNode values = schema.path("enum");
        if (!values.isMissingNode()
                && (!values.isArray() || values.isEmpty() || values.size() > MAX_ENUM_VALUES)) {
            errors.add(path + ".enum 必须是 1 到 " + MAX_ENUM_VALUES + " 个值的数组");
        }
    }

    private static boolean declaresType(JsonNode typeNode, String expected) {
        if (typeNode.isTextual()) return expected.equals(typeNode.asText());
        if (!typeNode.isArray()) return false;
        for (JsonNode item : typeNode) {
            if (expected.equals(item.asText())) return true;
        }
        return false;
    }

    private static String stripMarkdownFence(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        return normalized;
    }
}
