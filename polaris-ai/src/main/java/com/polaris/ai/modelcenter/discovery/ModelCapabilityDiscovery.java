package com.polaris.ai.modelcenter.discovery;

import com.polaris.ai.modelcenter.vo.ProviderConnectionRuntime;

/** 独立的模型能力发现契约；/models 只返回 ID 时不得调用本契约伪造能力。 */
public interface ModelCapabilityDiscovery {

    DiscoveredModelCapabilities discover(
            ProviderConnectionRuntime connection,
            String modelId);
}
