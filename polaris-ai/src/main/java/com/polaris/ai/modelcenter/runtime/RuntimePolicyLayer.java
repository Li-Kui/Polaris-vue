package com.polaris.ai.modelcenter.runtime;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;

/** RuntimePolicy 单层 nullable override；NULL 表示不覆盖上层。 */
public record RuntimePolicyLayer(
        Integer maxConcurrency,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Integer retryCount,
        BigDecimal qpsLimit,
        Integer priority,
        ObjectNode extraConfig) {

    public RuntimePolicyLayer {
        extraConfig = extraConfig == null
                ? JsonNodeFactory.instance.objectNode()
                : extraConfig.deepCopy();
    }

    @Override
    public ObjectNode extraConfig() {
        return extraConfig.deepCopy();
    }

    public static RuntimePolicyLayer empty() {
        return new RuntimePolicyLayer(
                null, null, null, null, null, null, null);
    }
}
