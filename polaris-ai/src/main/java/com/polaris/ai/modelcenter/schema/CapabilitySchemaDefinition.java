package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 版本化 Capability Schema 定义。Capability Code 保持字符串扩展点。 */
public record CapabilitySchemaDefinition(
        String code,
        String name,
        CapabilityKind kind,
        String category,
        int schemaVersion,
        SchemaNode schema,
        List<ConditionRuleDefinition> conditionRules,
        Map<String, UiFieldDefinition> uiSchema,
        Map<String, ParameterPolicyDefinition> parameterPolicy,
        Set<String> allowedAppliesTo) {

    public CapabilitySchemaDefinition {
        conditionRules = conditionRules == null ? List.of() : List.copyOf(conditionRules);
        uiSchema = uiSchema == null ? Map.of() : Map.copyOf(uiSchema);
        parameterPolicy = parameterPolicy == null ? Map.of() : Map.copyOf(parameterPolicy);
        allowedAppliesTo = allowedAppliesTo == null ? Set.of() : Set.copyOf(allowedAppliesTo);
    }

    public enum CapabilityKind {
        INVOCATION,
        FEATURE;

        @JsonCreator
        public static CapabilityKind fromJson(String value) {
            return value == null ? null
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toJson() {
            return name();
        }
    }
}
