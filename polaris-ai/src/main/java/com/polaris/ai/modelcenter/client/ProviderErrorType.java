package com.polaris.ai.modelcenter.client;

/** Provider 错误的稳定内部分类。 */
public enum ProviderErrorType {
    AUTH_FAILED,
    QUOTA_EXHAUSTED,
    RATE_LIMITED,
    MODEL_NOT_FOUND,
    ENDPOINT_NOT_FOUND,
    REQUEST_INVALID,
    PROVIDER_TIMEOUT,
    DNS_FAILED,
    CONNECTION_REFUSED,
    TLS_FAILED,
    PROVIDER_UNAVAILABLE,
    CONTENT_REJECTED,
    UNKNOWN
}
