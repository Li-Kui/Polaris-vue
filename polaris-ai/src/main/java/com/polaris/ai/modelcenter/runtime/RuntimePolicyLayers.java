package com.polaris.ai.modelcenter.runtime;

/** 模型级与 Invocation Capability 级策略层；逐字段继承在 Phase 4B 完成。 */
public record RuntimePolicyLayers(
        RuntimePolicyLayer model,
        RuntimePolicyLayer capability) {

    public RuntimePolicyLayers {
        model = model == null ? RuntimePolicyLayer.empty() : model;
        capability = capability == null
                ? RuntimePolicyLayer.empty() : capability;
    }
}
