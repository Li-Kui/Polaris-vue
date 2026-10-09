package com.polaris.ai.runtime;

/** 允许 Provider 层向 Runtime 暴露稳定错误分类，不暴露原始响应体。 */
public interface CategorizedModelExecutionError {

    String errorCategory();

    default String providerRequestId() {
        return null;
    }
}
