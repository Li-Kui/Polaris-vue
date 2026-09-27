package com.polaris.ai.modelcenter.schema;

import java.util.Set;

/** 条件规则对当前配置得出的服务端约束。 */
public record ConditionEvaluation(
        Set<String> requiredFields,
        Set<String> disabledFields,
        Set<String> hiddenFields) {

    public ConditionEvaluation {
        requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
        disabledFields = disabledFields == null ? Set.of() : Set.copyOf(disabledFields);
        hiddenFields = hiddenFields == null ? Set.of() : Set.copyOf(hiddenFields);
    }
}
