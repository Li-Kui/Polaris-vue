package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** Capability 字段的受限条件规则。 */
public record ConditionRuleDefinition(
        String target,
        RuleType type,
        ConditionExpression when) {

    public enum RuleType {
        REQUIRED_WHEN,
        ENABLED_WHEN,
        VISIBLE_WHEN;

        @JsonCreator
        public static RuleType fromJson(String value) {
            return value == null ? null
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toJson() {
            return name();
        }
    }
}
