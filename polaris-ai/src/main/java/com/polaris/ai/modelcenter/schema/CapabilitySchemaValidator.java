package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 严格校验 Capability config，不执行字符串到数值等隐式转换。 */
@Component
public class CapabilitySchemaValidator {

    public SchemaValidationResult validate(
            CapabilitySchemaDefinition definition,
            ObjectNode config,
            ConditionEvaluation conditions) {
        List<String> errors = new ArrayList<>();
        validateObject(definition.schema(), config, "$", conditions, errors);
        return new SchemaValidationResult(errors);
    }

    public void validateOrThrow(
            CapabilitySchemaDefinition definition,
            ObjectNode config,
            ConditionEvaluation conditions) {
        SchemaValidationResult result = validate(definition, config, conditions);
        if (!result.valid()) {
            throw new ServiceException(
                    "Capability config 校验失败：" + String.join("; ", result.errors()));
        }
    }

    private void validateObject(
            SchemaNode schema,
            JsonNode value,
            String path,
            ConditionEvaluation conditions,
            List<String> errors) {
        if (value == null || !value.isObject()) {
            errors.add(path + " 必须是 object");
            return;
        }
        ObjectNode object = (ObjectNode) value;
        Set<String> required = new LinkedHashSet<>(schema.required());
        if ("$".equals(path)) {
            required.addAll(conditions.requiredFields());
        }
        required.forEach(field -> {
            if (!object.has(field) || object.get(field).isNull()) {
                errors.add(path + "." + field + " 为必填字段");
            }
        });

        object.properties().forEach(entry -> {
            String field = entry.getKey();
            JsonNode fieldValue = entry.getValue();
            SchemaNode fieldSchema = schema.properties().get(field);
            if (fieldSchema == null) {
                errors.add(path + "." + field + " 是未允许的字段");
                return;
            }
            if ("$".equals(path) && conditions.disabledFields().contains(field)) {
                errors.add(path + "." + field + " 在当前条件下不可用");
                return;
            }
            validateValue(fieldSchema, fieldValue, path + "." + field, errors);
        });
    }

    private void validateValue(
            SchemaNode schema,
            JsonNode value,
            String path,
            List<String> errors) {
        if (value == null || value.isNull()) {
            errors.add(path + " 不允许为 null");
            return;
        }
        boolean typeMatches = switch (schema.type()) {
            case OBJECT -> value.isObject();
            case STRING -> value.isTextual();
            case INTEGER -> value.isIntegralNumber();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ARRAY -> value.isArray();
        };
        if (!typeMatches) {
            errors.add(path + " 类型必须是 " + schema.type().toJson());
            return;
        }

        if (!schema.enumValues().isEmpty()
                && schema.enumValues().stream().noneMatch(value::equals)) {
            errors.add(path + " 不在允许的 enum 中");
        }
        switch (schema.type()) {
            case OBJECT -> validateObject(
                    schema, value, path,
                    new ConditionEvaluation(Set.of(), Set.of(), Set.of()), errors);
            case STRING -> validateString(schema, value, path, errors);
            case INTEGER, NUMBER -> validateNumber(schema, value, path, errors);
            case ARRAY -> validateStringArray(value, path, errors);
            case BOOLEAN -> {
                // 类型检查已覆盖 boolean 的全部约束。
            }
        }
    }

    private void validateString(
            SchemaNode schema,
            JsonNode value,
            String path,
            List<String> errors) {
        int length = value.textValue().codePointCount(0, value.textValue().length());
        if (schema.minLength() != null && length < schema.minLength()) {
            errors.add(path + " 长度不能小于 " + schema.minLength());
        }
        if (schema.maxLength() != null && length > schema.maxLength()) {
            errors.add(path + " 长度不能大于 " + schema.maxLength());
        }
    }

    private void validateNumber(
            SchemaNode schema,
            JsonNode value,
            String path,
            List<String> errors) {
        BigDecimal number = value.decimalValue();
        if (schema.minimum() != null && number.compareTo(schema.minimum()) < 0) {
            errors.add(path + " 不能小于 " + schema.minimum());
        }
        if (schema.maximum() != null && number.compareTo(schema.maximum()) > 0) {
            errors.add(path + " 不能大于 " + schema.maximum());
        }
        if (schema.multipleOf() != null
                && number.remainder(schema.multipleOf()).compareTo(BigDecimal.ZERO) != 0) {
            errors.add(path + " 必须是 " + schema.multipleOf() + " 的倍数");
        }
    }

    private void validateStringArray(
            JsonNode value,
            String path,
            List<String> errors) {
        for (int index = 0; index < value.size(); index++) {
            if (!value.get(index).isTextual()) {
                errors.add(path + "[" + index + "] 必须是 string");
            }
        }
    }
}
