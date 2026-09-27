package com.polaris.ai.modelcenter.schema;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/** 默认阻止 Model Profile 放宽字段、条件与覆盖来源约束。 */
@Component
public class ModelProfileTighteningValidator {

    public void validate(
            CapabilitySchemaDefinition before,
            CapabilitySchemaDefinition after) {
        require(after.conditionRules().containsAll(before.conditionRules()),
                "Model Profile 不允许移除已有 Condition Rule");
        validateNode(before.schema(), after.schema(), "$");
        validatePolicies(before, after);
    }

    private void validateNode(SchemaNode before, SchemaNode after, String path) {
        require(before.type() == after.type(), path + " 不允许改变字段类型");
        require(after.required().containsAll(before.required()),
                path + " 不允许取消 required");
        require(before.properties().keySet().containsAll(after.properties().keySet()),
                path + " 不允许新增字段");
        require(enumTightened(before, after), path + " 不允许放宽 enum");
        require(minimumTightened(before.minimum(), after.minimum()),
                path + " 不允许降低 minimum");
        require(maximumTightened(before.maximum(), after.maximum()),
                path + " 不允许提高 maximum");
        require(minimumTightened(integer(before.minLength()), integer(after.minLength())),
                path + " 不允许降低 minLength");
        require(maximumTightened(integer(before.maxLength()), integer(after.maxLength())),
                path + " 不允许提高 maxLength");
        require(multipleOfTightened(before.multipleOf(), after.multipleOf()),
                path + " 不允许放宽 multipleOf");

        for (Map.Entry<String, SchemaNode> field : after.properties().entrySet()) {
            validateNode(before.properties().get(field.getKey()), field.getValue(),
                    path + "." + field.getKey());
        }
        if (before.items() != null && after.items() != null) {
            validateNode(before.items(), after.items(), path + "[]");
        }
    }

    private boolean enumTightened(SchemaNode before, SchemaNode after) {
        Set<com.fasterxml.jackson.databind.JsonNode> previous =
                Set.copyOf(before.enumValues());
        Set<com.fasterxml.jackson.databind.JsonNode> current =
                Set.copyOf(after.enumValues());
        if (previous.isEmpty()) {
            return true;
        }
        return !current.isEmpty() && previous.containsAll(current);
    }

    private boolean minimumTightened(BigDecimal before, BigDecimal after) {
        return before == null || after != null && after.compareTo(before) >= 0;
    }

    private boolean maximumTightened(BigDecimal before, BigDecimal after) {
        return before == null || after != null && after.compareTo(before) <= 0;
    }

    private boolean multipleOfTightened(BigDecimal before, BigDecimal after) {
        return before == null || after != null
                && after.remainder(before).compareTo(BigDecimal.ZERO) == 0;
    }

    private BigDecimal integer(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private void validatePolicies(
            CapabilitySchemaDefinition before,
            CapabilitySchemaDefinition after) {
        after.schema().properties().keySet().forEach(field -> {
            ParameterPolicyDefinition previous = before.parameterPolicy().getOrDefault(
                    field, new ParameterPolicyDefinition(false, Set.of()));
            ParameterPolicyDefinition current = after.parameterPolicy().getOrDefault(
                    field, new ParameterPolicyDefinition(false, Set.of()));
            require(!current.overridable() || previous.overridable(),
                    "Model Profile 不允许放宽 Parameter Policy: " + field);
            if (current.overridable() && previous.overridable()
                    && !previous.allowedSources().isEmpty()) {
                require(!current.allowedSources().isEmpty()
                                && previous.allowedSources().containsAll(
                                current.allowedSources()),
                        "Model Profile 不允许扩大 Parameter Source: " + field);
            }
        });
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
