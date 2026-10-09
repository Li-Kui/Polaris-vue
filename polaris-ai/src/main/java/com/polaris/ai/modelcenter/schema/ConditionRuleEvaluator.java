package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/** 执行受限 Condition DSL，不执行表达式语言或脚本。 */
@Component
public class ConditionRuleEvaluator {

    public ConditionEvaluation evaluate(
            java.util.List<ConditionRuleDefinition> rules,
            ObjectNode config) {
        Set<String> required = new LinkedHashSet<>();
        Set<String> disabled = new LinkedHashSet<>();
        Set<String> hidden = new LinkedHashSet<>();
        if (rules == null) {
            return new ConditionEvaluation(required, disabled, hidden);
        }
        for (ConditionRuleDefinition rule : rules) {
            boolean matched = matches(rule.when(), config);
            switch (rule.type()) {
                case REQUIRED_WHEN -> {
                    if (matched) {
                        required.add(rule.target());
                    }
                }
                case ENABLED_WHEN -> {
                    if (!matched) {
                        disabled.add(rule.target());
                    }
                }
                case VISIBLE_WHEN -> {
                    if (!matched) {
                        hidden.add(rule.target());
                    }
                }
            }
        }
        return new ConditionEvaluation(required, disabled, hidden);
    }

    public boolean matches(ConditionExpression expression, ObjectNode config) {
        if (!expression.all().isEmpty()) {
            return expression.all().stream().allMatch(item -> matches(item, config));
        }
        if (!expression.any().isEmpty()) {
            return expression.any().stream().anyMatch(item -> matches(item, config));
        }
        JsonNode actual = config.get(expression.field());
        boolean exists = actual != null && !actual.isNull();
        return switch (expression.operator()) {
            case EXISTS -> exists;
            case NOT_EXISTS -> !exists;
            case EQ -> exists && actual.equals(expression.value());
            case NE -> !exists || !actual.equals(expression.value());
            case IN -> exists && contains(expression.value(), actual);
            case NOT_IN -> !exists || !contains(expression.value(), actual);
            case GT -> compare(actual, expression.value()) > 0;
            case GTE -> compare(actual, expression.value()) >= 0;
            case LT -> compare(actual, expression.value()) < 0;
            case LTE -> compare(actual, expression.value()) <= 0;
        };
    }

    private boolean contains(JsonNode values, JsonNode actual) {
        if (values == null || !values.isArray()) {
            throw new IllegalStateException("IN/NOT_IN 的 value 必须是 array");
        }
        for (JsonNode value : values) {
            if (value.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    private int compare(JsonNode left, JsonNode right) {
        if (left == null || !left.isNumber() || right == null || !right.isNumber()) {
            throw new IllegalStateException("GT/GTE/LT/LTE 只支持数值比较");
        }
        BigDecimal leftValue = left.decimalValue();
        BigDecimal rightValue = right.decimalValue();
        return leftValue.compareTo(rightValue);
    }
}
