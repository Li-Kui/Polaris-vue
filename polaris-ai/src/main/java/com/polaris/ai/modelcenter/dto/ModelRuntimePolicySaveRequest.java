package com.polaris.ai.modelcenter.dto;

import java.math.BigDecimal;
import java.util.Map;

/** RuntimePolicy 的 nullable override 请求；null 表示继承，0 保持为有效值。 */
public record ModelRuntimePolicySaveRequest(
        String capabilityCode,
        Integer maxConcurrency,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Integer retryCount,
        BigDecimal qpsLimit,
        Integer priority,
        Map<String, Object> extraConfig) {

    public ModelRuntimePolicySaveRequest {
        extraConfig = extraConfig == null ? Map.of() : Map.copyOf(extraConfig);
    }
}
