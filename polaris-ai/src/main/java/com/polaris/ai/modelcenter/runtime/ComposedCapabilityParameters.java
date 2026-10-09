package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/** 请求级参数组合结果；Invocation 和激活 Feature 永不扁平化。 */
public record ComposedCapabilityParameters(
        ObjectNode invocation,
        Map<String, ObjectNode> features) {

    public ComposedCapabilityParameters {
        invocation = invocation == null
                ? JsonNodeFactory.instance.objectNode() : invocation.deepCopy();
        Map<String, ObjectNode> copied = new LinkedHashMap<>();
        if (features != null) {
            features.forEach((code, parameters) -> copied.put(
                    code, parameters == null
                            ? JsonNodeFactory.instance.objectNode()
                            : parameters.deepCopy()));
        }
        features = Map.copyOf(copied);
    }

    @Override
    public ObjectNode invocation() {
        return invocation.deepCopy();
    }

    @Override
    public Map<String, ObjectNode> features() {
        Map<String, ObjectNode> copied = new LinkedHashMap<>();
        features.forEach((code, parameters) ->
                copied.put(code, parameters.deepCopy()));
        return Map.copyOf(copied);
    }
}
