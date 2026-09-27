package com.polaris.ai.modelcenter.client;

/** Provider 错误的稳定内部分类。 */
public enum ProviderErrorType {
    AUTH_FAILED,
    QUOTA_EXHAUSTED,
    RATE_LIMITED,
    MODEL_NOT_FOUND,
    REQUEST_INVALID,
    PROVIDER_TIMEOUT,
    PROVIDER_UNAVAILABLE,
    CONTENT_REJECTED,
    UNKNOWN
}
