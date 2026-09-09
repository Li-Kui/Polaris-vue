package com.polaris.ai.workflow.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 面向已支持 JSON Schema 契约子集的轻量确定性输入校验器。 */
@Component
public class WorkflowInputValidator {

    private static final int MAX_ERRORS = 100;

    public List<String> validate(JsonNode schema, JsonNode input) {
        return validate(schema, input, "$");
    }

    public List<String> validate(JsonNode schema, JsonNode input, String rootPath) {
        List<String> errors = new ArrayList<>();
        validateValue(schema, input,
                rootPath == null || rootPath.isBlank() ? "$" : rootPath, errors);
        return errors;
    }

    private void validateValue(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (errors.size() >= MAX_ERRORS || schema == null || schema.isNull()
                || schema.isMissingNode() || !schema.isObject() || schema.isEmpty()) {
            return;
        }
        JsonNode typeNode = schema.path("type");
        String type = matchingType(typeNode, value);
        if (!typeNode.isMissingNode() && type == null) {
            add(errors, path + " 类型应为 " + expectedTypes(typeNode));
            return;
        }
        validateEnum(schema, value, path, errors);
        if (errors.size() >= MAX_ERRORS || value == null || value.isNull()) {
            return;
        }
        if ("object".equals(type) && value != null && value.isObject()) {
            validateObject(schema, value, path, errors);
        }
        if ("array".equals(type) && value != null && value.isArray()) {
            validateArray(schema, value, path, errors);
        }
        if ("string".equals(type) && value.isTextual()) {
            validateString(schema, value.asText(), path, errors);
        }
        if (("number".equals(type) || "integer".equals(type)) && value.isNumber()) {
            validateNumber(schema, value.decimalValue(), path, errors);
        }
    }

    private void validateObject(
            JsonNode schema, JsonNode value, String path, List<String> errors) {
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            required.forEach(name -> {
                if (!value.has(name.asText())) {
                    add(errors, path + "." + name.asText() + " 为必填字段");
                }
            });
        }
        JsonNode properties = schema.path("properties");
        Set<String> declared = new HashSet<>();
        if (properties.isObject()) {
            properties.fields().forEachRemaining(field -> {
                declared.add(field.getKey());
                if (value.has(field.getKey())) {
                    validateValue(field.getValue(), value.get(field.getKey()),
                            path + "." + field.getKey(), errors);
                }
            });
        }
        if (schema.has("additionalProperties")
                && schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").asBoolean()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext() && errors.size() < MAX_ERRORS) {
                String name = fields.next().getKey();
                if (!declared.contains(name)) {
                    add(errors, path + "." + name + " 为未允许的字段");
                }
            }
        }
    }

    private void validateArray(
            JsonNode schema, JsonNode value, String path, List<String> errors) {
        int size = value.size();
        if (schema.has("minItems") && size < schema.path("minItems").asInt()) {
            add(errors, path + " 元素数量不能少于 " + schema.path("minItems").asInt());
        }
        if (schema.has("maxItems") && size > schema.path("maxItems").asInt()) {
            add(errors, path + " 元素数量不能多于 " + schema.path("maxItems").asInt());
        }
        if (schema.path("uniqueItems").asBoolean(false)) {
            Set<JsonNode> unique = new HashSet<>();
            for (JsonNode item : value) {
                if (!unique.add(item)) {
                    add(errors, path + " 元素必须唯一");
                    break;
                }
            }
        }
        if (schema.has("items")) {
            for (int index = 0; index < value.size(); index++) {
                validateValue(schema.path("items"), value.get(index),
                        path + "[" + index + "]", errors);
            }
        }
    }

    private void validateString(
            JsonNode schema, String value, String path, List<String> errors) {
        int length = value.codePointCount(0, value.length());
        if (schema.has("minLength") && length < schema.path("minLength").asInt()) {
            add(errors, path + " 长度不能少于 " + schema.path("minLength").asInt());
        }
        if (schema.has("maxLength") && length > schema.path("maxLength").asInt()) {
            add(errors, path + " 长度不能多于 " + schema.path("maxLength").asInt());
        }
        if (schema.hasNonNull("pattern")) {
            try {
                if (!Pattern.compile(schema.path("pattern").asText()).matcher(value).find()) {
                    add(errors, path + " 格式不符合要求");
                }
            } catch (PatternSyntaxException e) {
                add(errors, path + " 校验规则无效");
            }
        }
    }

    private void validateNumber(
            JsonNode schema, BigDecimal value, String path, List<String> errors) {
        if (schema.has("minimum")
                && value.compareTo(schema.path("minimum").decimalValue()) < 0) {
            add(errors, path + " 不能小于 " + schema.path("minimum").decimalValue());
        }
        if (schema.has("maximum")
                && value.compareTo(schema.path("maximum").decimalValue()) > 0) {
            add(errors, path + " 不能大于 " + schema.path("maximum").decimalValue());
        }
        if (schema.has("exclusiveMinimum")
                && value.compareTo(schema.path("exclusiveMinimum").decimalValue()) <= 0) {
            add(errors, path + " 必须大于 " + schema.path("exclusiveMinimum").decimalValue());
        }
        if (schema.has("exclusiveMaximum")
                && value.compareTo(schema.path("exclusiveMaximum").decimalValue()) >= 0) {
            add(errors, path + " 必须小于 " + schema.path("exclusiveMaximum").decimalValue());
        }
    }

    private void validateEnum(
            JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema.has("const") && !schema.path("const").equals(value)) {
            add(errors, path + " 必须等于 " + schema.path("const"));
        }
        JsonNode values = schema.path("enum");
        if (values.isArray()) {
            boolean matched = false;
            for (JsonNode candidate : values) {
                if (candidate.equals(value)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                add(errors, path + " 必须是允许的枚举值");
            }
        }
    }

    private void add(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        }
    }

    private boolean matches(String type, JsonNode value) {
        if (value == null || value.isNull()) {
            return "null".equals(type);
        }
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private String matchingType(JsonNode typeNode, JsonNode value) {
        if (typeNode == null || typeNode.isMissingNode() || typeNode.isNull()) return "";
        if (typeNode.isTextual()) {
            return matches(typeNode.asText(), value) ? typeNode.asText() : null;
        }
        if (typeNode.isArray()) {
            for (JsonNode item : typeNode) {
                if (item.isTextual() && matches(item.asText(), value)) return item.asText();
            }
        }
        return null;
    }

    private String expectedTypes(JsonNode typeNode) {
        if (typeNode == null || typeNode.isMissingNode()) return "未声明类型";
        if (typeNode.isTextual()) return typeNode.asText();
        if (typeNode.isArray()) {
            List<String> types = new ArrayList<>();
            typeNode.forEach(item -> types.add(item.asText()));
            return String.join(" 或 ", types);
        }
        return "有效 JSON 类型";
    }
}
