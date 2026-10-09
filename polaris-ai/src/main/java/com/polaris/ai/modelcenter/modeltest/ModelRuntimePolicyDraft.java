package com.polaris.ai.modelcenter.modeltest;

import java.math.BigDecimal;

/** 未保存模型测试使用的通用运行策略，不承载 Capability 专属字段。 */
public record ModelRuntimePolicyDraft(
        Integer maxConcurrency,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Integer retryCount,
        BigDecimal qpsLimit,
        Integer priority) {

    public static ModelRuntimePolicyDraft empty() {
        return new ModelRuntimePolicyDraft(null, null, null, null, null, null);
    }
}
