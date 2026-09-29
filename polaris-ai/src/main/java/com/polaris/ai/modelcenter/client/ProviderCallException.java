package com.polaris.ai.modelcenter.client;

import com.polaris.ai.runtime.CategorizedModelExecutionError;

/** 不携带 Provider 原始错误体的受控调用异常。 */
public class ProviderCallException extends RuntimeException
        implements CategorizedModelExecutionError {

    private final ProviderErrorType errorType;
    private final Integer httpStatus;
    private final String providerRequestId;
    private final long latencyMillis;

    public ProviderCallException(
            ProviderErrorType errorType,
            Integer httpStatus,
            String providerRequestId,
            long latencyMillis) {
        this(errorType, httpStatus, providerRequestId, latencyMillis, null);
    }

    public ProviderCallException(
            ProviderErrorType errorType,
            Integer httpStatus,
            String providerRequestId,
            long latencyMillis,
            Throwable cause) {
        super("PROVIDER_" + errorType.name(), cause);
        this.errorType = errorType;
        this.httpStatus = httpStatus;
        this.providerRequestId = providerRequestId;
        this.latencyMillis = latencyMillis;
    }

    public ProviderErrorType errorType() {
        return errorType;
    }

    @Override
    public String errorCategory() {
        return errorType.name();
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    @Override
    public String providerRequestId() {
        return providerRequestId;
    }

    public long latencyMillis() {
        return latencyMillis;
    }
}
