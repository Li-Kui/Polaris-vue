package com.polaris.ai.modelcenter.client;

import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/** 将 Provider HTTP/传输错误转换为稳定内部分类。 */
@Component
public class ProviderErrorTranslator {

    public ProviderCallException fromResponse(ProviderHttpResponse response) {
        int status = response.statusCode();
        String body = response.body() == null
                ? "" : response.body().toLowerCase(Locale.ROOT);
        ProviderErrorType type;
        if ((status == 400 || status == 403 || status == 429)
                && containsAny(body, "insufficient_quota", "quota exhausted",
                "quota_exhausted", "billing")) {
            type = ProviderErrorType.QUOTA_EXHAUSTED;
        } else if ((status == 400 || status == 403)
                && containsAny(body, "content_filter", "content policy",
                "safety", "moderation")) {
            type = ProviderErrorType.CONTENT_REJECTED;
        } else if (status == 401 || status == 403) {
            type = ProviderErrorType.AUTH_FAILED;
        } else if (status == 404) {
            type = ProviderErrorType.MODEL_NOT_FOUND;
        } else if (status == 408 || status == 504) {
            type = ProviderErrorType.PROVIDER_TIMEOUT;
        } else if (status == 429) {
            type = ProviderErrorType.RATE_LIMITED;
        } else if (status == 400 || status == 409 || status == 422) {
            type = ProviderErrorType.REQUEST_INVALID;
        } else if (status >= 500) {
            type = ProviderErrorType.PROVIDER_UNAVAILABLE;
        } else {
            type = ProviderErrorType.UNKNOWN;
        }
        return new ProviderCallException(
                type, status, response.providerRequestId(),
                response.latencyMillis());
    }

    public ProviderCallException fromTransport(
            Throwable failure,
            long latencyMillis) {
        ProviderErrorType type = failure instanceof HttpTimeoutException
                || failure instanceof TimeoutException
                ? ProviderErrorType.PROVIDER_TIMEOUT
                : failure instanceof ConnectException
                ? ProviderErrorType.PROVIDER_UNAVAILABLE
                : ProviderErrorType.UNKNOWN;
        return new ProviderCallException(type, null, null, latencyMillis);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
