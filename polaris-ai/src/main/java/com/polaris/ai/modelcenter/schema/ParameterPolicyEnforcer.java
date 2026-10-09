package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 按来源执行 Parameter Policy，字段缺失表示不覆盖。 */
@Component
public class ParameterPolicyEnforcer {

    private final SchemaConfigNormalizer normalizer;
    private final ConditionRuleEvaluator conditionEvaluator;
    private final CapabilitySchemaValidator validator;

    public ParameterPolicyEnforcer(
            SchemaConfigNormalizer normalizer,
            ConditionRuleEvaluator conditionEvaluator,
            CapabilitySchemaValidator validator) {
        this.normalizer = normalizer;
        this.conditionEvaluator = conditionEvaluator;
        this.validator = validator;
    }

    public ObjectNode apply(
            CapabilitySchemaDefinition definition,
            ObjectNode baseConfig,
            JsonNode override,
            ParameterPolicyDefinition.ParameterSource source) {
        if (source == null) {
            throw new ServiceException("Override source 不能为空");
        }
        if (override == null || override.isNull()) {
            return validate(definition, baseConfig.deepCopy());
        }
        if (!override.isObject()) {
            throw new ServiceException("Parameter override 必须是 object");
        }

        ObjectNode result = baseConfig.deepCopy();
        for (Map.Entry<String, JsonNode> field : override.properties()) {
            String name = field.getKey();
            JsonNode value = field.getValue();
            if (!definition.schema().properties().containsKey(name)) {
                throw new ServiceException("Parameter override 包含未知字段: " + name);
            }
            if (value == null || value.isNull()) {
                throw new ServiceException("Parameter override 不允许 null: " + name);
            }
            ParameterPolicyDefinition policy = definition.parameterPolicy().get(name);
            if (policy == null || !policy.overridable()) {
                throw new ServiceException("Parameter 不允许覆盖: " + name);
            }
            if (!policy.allowedSources().isEmpty()
                    && !policy.allowedSources().contains(source)) {
                throw new ServiceException(
                        "Parameter 不允许来自 " + source + " 的覆盖: " + name);
            }
            result.set(name, value.deepCopy());
        }
        return validate(definition, result);
    }

    private ObjectNode validate(
            CapabilitySchemaDefinition definition,
            ObjectNode config) {
        ObjectNode normalized = normalizer.normalize(config);
        ConditionEvaluation conditions = conditionEvaluator.evaluate(
                definition.conditionRules(), normalized);
        validator.validateOrThrow(definition, normalized, conditions);
        return normalized;
    }
}
