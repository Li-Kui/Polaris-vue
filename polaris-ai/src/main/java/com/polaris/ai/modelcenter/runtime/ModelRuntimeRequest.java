package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.CapabilityInvocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 解析单次运行时所需的模型、强类型调用、Feature 激活与多来源覆盖。 */
public record ModelRuntimeRequest<I extends CapabilityInvocation>(
        Long modelId,
        I invocation,
        Set<String> requestedFeatures,
        List<CapabilityParameterOverride> overrides) {

    public ModelRuntimeRequest {
        modelId = Objects.requireNonNull(modelId, "modelId");
        invocation = Objects.requireNonNull(invocation, "invocation");
        requestedFeatures = requestedFeatures == null
                ? Set.of() : Set.copyOf(requestedFeatures);
        overrides = overrides == null ? List.of() : List.copyOf(overrides);
    }
}
