package com.polaris.ai.runtime;

import java.math.BigDecimal;

/** 与数据库 Entity 解耦的已解析模型运行策略。 */
public record RuntimePolicySpec(
        Integer maxConcurrency,
        int connectTimeoutMs,
        int readTimeoutMs,
        int retryCount,
        BigDecimal qpsLimit,
        int priority) {

    public RuntimePolicySpec {
        if (maxConcurrency != null && maxConcurrency < 1
                || connectTimeoutMs < 100 || connectTimeoutMs > 60_000
                || readTimeoutMs < 100 || readTimeoutMs > 300_000
                || retryCount < 0 || retryCount > 10
                || qpsLimit != null && (qpsLimit.signum() <= 0
                || qpsLimit.compareTo(new BigDecimal("10000")) > 0)
                || priority < -10_000 || priority > 10_000) {
            throw new IllegalArgumentException("RuntimePolicySpec 无效");
        }
    }
}
