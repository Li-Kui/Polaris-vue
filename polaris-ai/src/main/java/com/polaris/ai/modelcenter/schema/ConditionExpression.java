package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;

/** 受限条件表达式：单条件或 all/any 组合。 */
public record ConditionExpression(
        String field,
        Operator operator,
        JsonNode value,
        List<ConditionExpression> all,
        List<ConditionExpression> any) {

    public ConditionExpression {
        value = value == null || value.isNull() ? null : value.deepCopy();
        all = all == null ? List.of() : List.copyOf(all);
        any = any == null ? List.of() : List.copyOf(any);
    }

    @Override
    public JsonNode value() {
        return value == null ? null : value.deepCopy();
    }

    public enum Operator {
        EQ,
        NE,
        IN,
        NOT_IN,
        EXISTS,
        NOT_EXISTS,
        GT,
        GTE,
        LT,
        LTE;

        @JsonCreator
        public static Operator fromJson(String value) {
            return value == null ? null
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String toJson() {
            return name();
        }
    }
}
