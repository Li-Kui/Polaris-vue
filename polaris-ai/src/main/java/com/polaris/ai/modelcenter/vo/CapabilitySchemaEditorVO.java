package com.polaris.ai.modelcenter.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.polaris.ai.modelcenter.schema.*;

import java.util.*;

/** 编辑器专用的纯 JSON Schema 视图，避免把 JsonNode 实现细节暴露给前端。 */
public record CapabilitySchemaEditorVO(
        String code,
        String name,
        String kind,
        String category,
        int schemaVersion,
        Map<String, Object> schema,
        List<Map<String, Object>> conditionRules,
        Map<String, Map<String, Object>> uiSchema,
        Map<String, Map<String, Object>> parameterPolicy,
        Set<String> allowedAppliesTo) {

    public CapabilitySchemaEditorVO {
        schema = schema == null ? Map.of() : Map.copyOf(schema);
        conditionRules = conditionRules == null
                ? List.of() : List.copyOf(conditionRules);
        uiSchema = uiSchema == null ? Map.of() : Map.copyOf(uiSchema);
        parameterPolicy = parameterPolicy == null
                ? Map.of() : Map.copyOf(parameterPolicy);
        allowedAppliesTo = allowedAppliesTo == null
                ? Set.of() : Set.copyOf(allowedAppliesTo);
    }

    public static CapabilitySchemaEditorVO from(
            CapabilitySchemaDefinition definition) {
        Map<String, Map<String, Object>> ui = new LinkedHashMap<>();
        definition.uiSchema().forEach((field, value) ->
                ui.put(field, uiField(value)));
        Map<String, Map<String, Object>> policies = new LinkedHashMap<>();
        definition.parameterPolicy().forEach((field, value) ->
                policies.put(field, parameterPolicy(value)));
        return new CapabilitySchemaEditorVO(
                definition.code(), definition.name(), definition.kind().name(),
                definition.category(), definition.schemaVersion(),
                schemaNode(definition.schema()),
                definition.conditionRules().stream()
                        .map(CapabilitySchemaEditorVO::conditionRule)
                        .toList(),
                ui, policies, definition.allowedAppliesTo());
    }

    private static Map<String, Object> schemaNode(SchemaNode node) {
        if (node == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "type", node.type() == null
                ? null : node.type().toJson());
        put(result, "title", node.title());
        put(result, "description", node.description());
        if (!node.properties().isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            node.properties().forEach((field, value) ->
                    properties.put(field, schemaNode(value)));
            result.put("properties", properties);
        }
        if (!node.required().isEmpty()) {
            result.put("required", node.required());
        }
        put(result, "additionalProperties", node.additionalProperties());
        if (!node.enumValues().isEmpty()) {
            result.put("enum", node.enumValues().stream()
                    .map(CapabilitySchemaEditorVO::jsonValue).toList());
        }
        put(result, "default", jsonValue(node.defaultValue()));
        put(result, "minimum", node.minimum());
        put(result, "maximum", node.maximum());
        put(result, "multipleOf", node.multipleOf());
        put(result, "minLength", node.minLength());
        put(result, "maxLength", node.maxLength());
        if (node.items() != null) {
            result.put("items", schemaNode(node.items()));
        }
        put(result, "$remove", node.remove());
        return result;
    }

    private static Map<String, Object> uiField(UiFieldDefinition field) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "component", field.component() == null
                ? null : field.component().toJson());
        put(result, "customComponent", field.customComponent());
        put(result, "optionsResolver", field.optionsResolver());
        put(result, "order", field.order());
        put(result, "span", field.span());
        put(result, "group", field.group());
        put(result, "placeholder", field.placeholder());
        put(result, "step", field.step());
        return result;
    }

    private static Map<String, Object> parameterPolicy(
            ParameterPolicyDefinition policy) {
        return Map.of(
                "overridable", policy.overridable(),
                "allowedSources", policy.allowedSources().stream()
                        .map(Enum::name).toList());
    }

    private static Map<String, Object> conditionRule(
            ConditionRuleDefinition rule) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "target", rule.target());
        put(result, "type", rule.type() == null
                ? null : rule.type().name());
        put(result, "when", conditionExpression(rule.when()));
        return result;
    }

    private static Map<String, Object> conditionExpression(
            ConditionExpression expression) {
        if (expression == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "field", expression.field());
        put(result, "operator", expression.operator() == null
                ? null : expression.operator().name());
        put(result, "value", jsonValue(expression.value()));
        if (!expression.all().isEmpty()) {
            result.put("all", expression.all().stream()
                    .map(CapabilitySchemaEditorVO::conditionExpression).toList());
        }
        if (!expression.any().isEmpty()) {
            result.put("any", expression.any().stream()
                    .map(CapabilitySchemaEditorVO::conditionExpression).toList());
        }
        return result;
    }

    private static Object jsonValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isIntegralNumber()) {
            return value.canConvertToLong()
                    ? value.longValue() : value.bigIntegerValue();
        }
        if (value.isFloatingPointNumber()) {
            return value.decimalValue();
        }
        if (value.isArray()) {
            List<Object> result = new ArrayList<>();
            value.forEach(item -> result.add(jsonValue(item)));
            return List.copyOf(result);
        }
        if (value.isObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            value.fieldNames().forEachRemaining(field ->
                    put(result, field, jsonValue(value.get(field))));
            return Map.copyOf(result);
        }
        return value.asText();
    }

    private static void put(
            Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
