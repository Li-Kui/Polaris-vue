package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.modelcenter.schema.ParameterPolicyDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 单一来源的 Invocation / Feature 参数覆盖。 */
public record CapabilityParameterOverride(
        ParameterPolicyDefinition.ParameterSource source,
        Map<String, Object> invocation,
        Map<String, Map<String, Object>> features) {

    public CapabilityParameterOverride {
        source = Objects.requireNonNull(source, "source");
        invocation = invocation == null ? Map.of() : Map.copyOf(invocation);
        if (features == null) {
            features = Map.of();
        } else {
            Map<String, Map<String, Object>> copied = new LinkedHashMap<>();
            features.forEach((code, parameters) -> copied.put(
                    code, parameters == null
                            ? Map.of() : Map.copyOf(parameters)));
            features = Map.copyOf(copied);
        }
    }
}
