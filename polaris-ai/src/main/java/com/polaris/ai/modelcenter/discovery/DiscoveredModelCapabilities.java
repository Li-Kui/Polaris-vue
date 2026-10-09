package com.polaris.ai.modelcenter.discovery;

import java.util.Set;

/** 能力发现结果，与远程模型列表发现保持独立。 */
public record DiscoveredModelCapabilities(
        String modelId,
        Set<String> capabilities) {

    public DiscoveredModelCapabilities {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
