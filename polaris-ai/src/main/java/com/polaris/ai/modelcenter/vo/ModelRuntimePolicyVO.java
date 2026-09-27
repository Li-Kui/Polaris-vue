package com.polaris.ai.modelcenter.vo;

import java.math.BigDecimal;
import java.util.Map;

/** Model Editor RuntimePolicy 三态视图。 */
public record ModelRuntimePolicyVO(
        String capabilityCode,
        Integer maxConcurrency,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Integer retryCount,
        BigDecimal qpsLimit,
        Integer priority,
        Map<String, Object> extraConfig) {

    public ModelRuntimePolicyVO {
        extraConfig = extraConfig == null ? Map.of() : Map.copyOf(extraConfig);
    }
}
