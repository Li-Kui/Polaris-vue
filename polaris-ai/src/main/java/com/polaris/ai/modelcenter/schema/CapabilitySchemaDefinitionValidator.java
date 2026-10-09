package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 对受控 Schema 子集执行启动期结构校验。 */
final class CapabilitySchemaDefinitionValidator {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final int MAX_CONDITION_DEPTH = 3;
    private static final int MAX_CONDITION_COUNT = 20;

    void validate(CapabilitySchemaDefinition definition) {
        require(definition != null, "Schema 定义不能为空");
        require(matchesCode(definition.code()), "Capability code 格式无效");
        require(hasText(definition.name()), "Capability name 不能为空");
        require(definition.kind() != null, "Capability kind 不能为空");
        require(hasText(definition.category()), "Capability category 不能为空");
        require(definition.schemaVersion() > 0, "schemaVersion 必须是正整数");
        validateObjectSchema(definition.schema(), "Capability schema");
        Set<String> fields = definition.schema().properties().keySet();
        validateRules(definition.conditionRules(), fields);
        validateUiSchema(definition.uiSchema(), fields);
        validateParameterPolicy(definition.parameterPolicy(), fields);
        validateAppliesTo(definition);
    }

    void validateObjectSchema(SchemaNode schema, String label) {
        require(schema != null, label + " 不能为空");
        require(schema.type() == SchemaNode.ValueType.OBJECT,
                label + " 根节点必须是 object");
        validateNode(schema, "$");
    }

    private void validateNode(SchemaNode node, String path) {
        require(node.type() != null, path + " 缺少 type");
        require(!Boolean.TRUE.equals(node.remove()),
                path + " 的 $remove 不允许出现在 Capability Base");

        switch (node.type()) {
            case OBJECT -> validateObject(node, path);
            case ARRAY -> validateArray(node, path);
            case STRING -> validateString(node, path);
            case INTEGER, NUMBER -> validateNumber(node, path);
            case BOOLEAN -> validateBoolean(node, path);
        }
        validateEnumAndDefault(node, path);
        if (node.type() != SchemaNode.ValueType.OBJECT) {
            require(node.additionalProperties() == null,
                    path + " 只有 object 可以声明 additionalProperties");
        }
    }

    private void validateObject(SchemaNode node, String path) {
        require(Boolean.FALSE.equals(node.additionalProperties()),
                path + " 必须显式设置 additionalProperties=false");
        require(node.items() == null, path + " object 不能声明 items");
        Set<String> required = new HashSet<>();
        for (String field : node.required()) {
            require(hasText(field), path + " required 包含空字段");
            require(required.add(field), path + " required 字段重复: " + field);
            require(node.properties().containsKey(field),
                    path + " required 引用不存在字段: " + field);
        }
        node.properties().forEach((field, child) -> {
            require(hasText(field), path + " properties 包含空字段");
            require(child != null, path + "." + field + " 定义不能为空");
            validateNode(child, path + "." + field);
        });
    }

    private void validateArray(SchemaNode node, String path) {
        require(node.properties().isEmpty() && node.required().isEmpty(),
                path + " array 不能声明 properties/required");
        require(node.items() != null
                        && node.items().type() == SchemaNode.ValueType.STRING,
                path + " 第一版仅支持 array<string>");
        validateNode(node.items(), path + "[]");
    }

    private void validateString(SchemaNode node, String path) {
        validateScalarShape(node, path);
        require(node.minimum() == null && node.maximum() == null
                        && node.multipleOf() == null,
                path + " string 不能声明数值约束");
        require(node.minLength() == null || node.minLength() >= 0,
                path + " minLength 不能小于 0");
        require(node.maxLength() == null || node.maxLength() >= 0,
                path + " maxLength 不能小于 0");
        require(node.minLength() == null || node.maxLength() == null
                        || node.minLength() <= node.maxLength(),
                path + " minLength 不能大于 maxLength");
    }

    private void validateNumber(SchemaNode node, String path) {
        validateScalarShape(node, path);
        require(node.minLength() == null && node.maxLength() == null,
                path + " number/integer 不能声明长度约束");
        require(node.minimum() == null || node.maximum() == null
                        || node.minimum().compareTo(node.maximum()) <= 0,
                path + " minimum 不能大于 maximum");
        require(node.multipleOf() == null
                        || node.multipleOf().compareTo(BigDecimal.ZERO) > 0,
                path + " multipleOf 必须大于 0");
    }

    private void validateBoolean(SchemaNode node, String path) {
        validateScalarShape(node, path);
        require(node.minimum() == null && node.maximum() == null
                        && node.multipleOf() == null
                        && node.minLength() == null && node.maxLength() == null,
                path + " boolean 不能声明数值或长度约束");
    }

    private void validateScalarShape(SchemaNode node, String path) {
        require(node.properties().isEmpty() && node.required().isEmpty()
                        && node.items() == null,
                path + " 标量类型不能声明 properties/required/items");
    }

    private void validateEnumAndDefault(SchemaNode node, String path) {
        Set<JsonNode> uniqueValues = new HashSet<>();
        for (JsonNode value : node.enumValues()) {
            require(value != null && matchesType(value, node.type()),
                    path + " enum 值与字段类型不匹配");
            require(uniqueValues.add(value), path + " enum 包含重复值");
            validateLiteralConstraints(node, value, path + " enum");
        }
        JsonNode defaultValue = node.defaultValue();
        if (defaultValue != null) {
            require(!defaultValue.isNull(), path + " 第一版不支持 null default");
            require(matchesType(defaultValue, node.type()),
                    path + " default 值与字段类型不匹配");
            if (!node.enumValues().isEmpty()) {
                require(node.enumValues().stream().anyMatch(defaultValue::equals),
                        path + " default 必须位于 enum 中");
            }
            validateLiteralConstraints(node, defaultValue, path + " default");
        }
    }

    private void validateLiteralConstraints(
            SchemaNode schema,
            JsonNode value,
            String path) {
        if (schema.type() == SchemaNode.ValueType.STRING) {
            int length = value.textValue().codePointCount(0, value.textValue().length());
            require(schema.minLength() == null || length >= schema.minLength(),
                    path + " 长度小于 minLength");
            require(schema.maxLength() == null || length <= schema.maxLength(),
                    path + " 长度大于 maxLength");
        }
        if (schema.type() == SchemaNode.ValueType.INTEGER
                || schema.type() == SchemaNode.ValueType.NUMBER) {
            BigDecimal number = value.decimalValue();
            require(schema.minimum() == null || number.compareTo(schema.minimum()) >= 0,
                    path + " 小于 minimum");
            require(schema.maximum() == null || number.compareTo(schema.maximum()) <= 0,
                    path + " 大于 maximum");
            require(schema.multipleOf() == null
                            || number.remainder(schema.multipleOf())
                            .compareTo(BigDecimal.ZERO) == 0,
                    path + " 不是 multipleOf 的倍数");
        }
    }

    private boolean matchesType(JsonNode value, SchemaNode.ValueType type) {
        return switch (type) {
            case STRING -> value.isTextual();
            case INTEGER -> value.isIntegralNumber();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ARRAY -> value.isArray()
                    && value.valueStream().allMatch(JsonNode::isTextual);
            case OBJECT -> value.isObject();
        };
    }

    private void validateRules(
            List<ConditionRuleDefinition> rules,
            Set<String> fields) {
        int[] conditionCount = {0};
        for (ConditionRuleDefinition rule : rules) {
            require(rule != null && rule.type() != null && rule.when() != null,
                    "conditionRules 结构无效");
            require(fields.contains(rule.target()),
                    "Condition target 引用不存在字段: " + rule.target());
            validateExpression(rule.when(), fields, 1, conditionCount);
        }
        require(conditionCount[0] <= MAX_CONDITION_COUNT,
                "Condition 数量不能超过 " + MAX_CONDITION_COUNT);
    }

    private void validateExpression(
            ConditionExpression expression,
            Set<String> fields,
            int depth,
            int[] conditionCount) {
        require(depth <= MAX_CONDITION_DEPTH,
                "Condition 嵌套深度不能超过 " + MAX_CONDITION_DEPTH);
        boolean leaf = hasText(expression.field()) || expression.operator() != null;
        boolean all = !expression.all().isEmpty();
        boolean any = !expression.any().isEmpty();
        require((leaf ? 1 : 0) + (all ? 1 : 0) + (any ? 1 : 0) == 1,
                "Condition 必须且只能是 leaf/all/any 之一");
        if (leaf) {
            conditionCount[0]++;
            require(fields.contains(expression.field()),
                    "Condition field 引用不存在字段: " + expression.field());
            require(expression.operator() != null, "Condition operator 不能为空");
            boolean existence = expression.operator() == ConditionExpression.Operator.EXISTS
                    || expression.operator() == ConditionExpression.Operator.NOT_EXISTS;
            require(existence == (expression.value() == null),
                    "EXISTS/NOT_EXISTS 不接受 value，其他 operator 必须有 value");
            if (expression.operator() == ConditionExpression.Operator.IN
                    || expression.operator() == ConditionExpression.Operator.NOT_IN) {
                require(expression.value() != null && expression.value().isArray(),
                        "IN/NOT_IN 的 value 必须是 array");
            }
            if (expression.operator() == ConditionExpression.Operator.GT
                    || expression.operator() == ConditionExpression.Operator.GTE
                    || expression.operator() == ConditionExpression.Operator.LT
                    || expression.operator() == ConditionExpression.Operator.LTE) {
                require(expression.value() != null && expression.value().isNumber(),
                        "GT/GTE/LT/LTE 的 value 必须是 number");
            }
            return;
        }
        List<ConditionExpression> children = all ? expression.all() : expression.any();
        require(!children.isEmpty(), "Condition all/any 不能为空");
        children.forEach(child -> {
            require(child != null, "Condition 子表达式不能为空");
            validateExpression(child, fields, depth + 1, conditionCount);
        });
    }

    private void validateUiSchema(
            Map<String, UiFieldDefinition> uiSchema,
            Set<String> fields) {
        uiSchema.forEach((field, ui) -> {
            require(fields.contains(field),
                    "uiSchema 引用不存在字段: " + field);
            require(ui != null && ui.component() != null,
                    "uiSchema component 不能为空: " + field);
            require(ui.component() == UiFieldDefinition.Component.CUSTOM
                            ? hasText(ui.customComponent())
                            : !hasText(ui.customComponent()),
                    "customComponent 仅能与 CUSTOM component 一起使用");
            require(ui.order() == null || ui.order() >= 0,
                    "uiSchema order 不能小于 0: " + field);
            require(ui.span() == null || ui.span() >= 1 && ui.span() <= 24,
                    "uiSchema span 必须在 1..24: " + field);
            require(ui.step() == null || ui.step().compareTo(BigDecimal.ZERO) > 0,
                    "uiSchema step 必须大于 0: " + field);
        });
    }

    private void validateParameterPolicy(
            Map<String, ParameterPolicyDefinition> policy,
            Set<String> fields) {
        policy.forEach((field, value) -> {
            require(fields.contains(field),
                    "parameterPolicy 引用不存在字段: " + field);
            require(value != null, "parameterPolicy 不能为空: " + field);
            require(value.overridable() || value.allowedSources().isEmpty(),
                    "不可覆盖字段不能声明 allowedSources: " + field);
        });
    }

    private void validateAppliesTo(CapabilitySchemaDefinition definition) {
        if (definition.kind() == CapabilitySchemaDefinition.CapabilityKind.INVOCATION) {
            require(definition.allowedAppliesTo().isEmpty(),
                    "Invocation Capability 不能声明 allowedAppliesTo");
            return;
        }
        require(!definition.allowedAppliesTo().isEmpty(),
                "Feature Capability 必须声明 allowedAppliesTo");
        definition.allowedAppliesTo().forEach(code ->
                require(matchesCode(code), "allowedAppliesTo code 格式无效"));
    }

    private boolean matchesCode(String value) {
        return value != null && CODE_PATTERN.matcher(value).matches();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
