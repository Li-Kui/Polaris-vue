package com.polaris.ai.modelcenter.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/** 按 Default→Normalize→Condition→Validate 处理待保存 Capability config。 */
@Component
public class CapabilityConfigProcessor {

    private final SchemaDefaultMaterializer defaultMaterializer;
    private final SchemaConfigNormalizer normalizer;
    private final ConditionRuleEvaluator conditionEvaluator;
    private final CapabilitySchemaValidator validator;

    public CapabilityConfigProcessor(
            SchemaDefaultMaterializer defaultMaterializer,
            SchemaConfigNormalizer normalizer,
            ConditionRuleEvaluator conditionEvaluator,
            CapabilitySchemaValidator validator) {
        this.defaultMaterializer = defaultMaterializer;
        this.normalizer = normalizer;
        this.conditionEvaluator = conditionEvaluator;
        this.validator = validator;
    }

    public ObjectNode process(
            CapabilitySchemaDefinition definition,
            JsonNode userInput) {
        ObjectNode materialized = defaultMaterializer.materialize(
                definition.schema(), userInput);
        ObjectNode normalized = normalizer.normalize(materialized);
        ConditionEvaluation conditions = conditionEvaluator.evaluate(
                definition.conditionRules(), normalized);
        validator.validateOrThrow(definition, normalized, conditions);
        return normalized;
    }
}
