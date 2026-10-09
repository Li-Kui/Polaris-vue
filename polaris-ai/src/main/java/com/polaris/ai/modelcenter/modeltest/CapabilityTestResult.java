package com.polaris.ai.modelcenter.modeltest;

import java.util.Map;

/** 不含 Provider 原始响应体的模型测试结果。 */
public record CapabilityTestResult(
        boolean success,
        String code,
        String message,
        Map<String, Object> metrics) {

    public CapabilityTestResult {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}
