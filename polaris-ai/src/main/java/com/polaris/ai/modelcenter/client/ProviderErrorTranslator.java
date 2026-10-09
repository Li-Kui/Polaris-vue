package com.polaris.ai.modelcenter.client;

import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
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
            // A missing route (including an HTML 404) is not evidence of a missing model.
            type = containsAny(body, "model_not_found", "modelnotfound", "invalidmodel",
                    "model does not exist", "model not found", "model is not found")
                    ? ProviderErrorType.MODEL_NOT_FOUND : ProviderErrorType.ENDPOINT_NOT_FOUND;
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
        ProviderErrorType type;
        if (causedBy(failure, HttpTimeoutException.class)
                || causedBy(failure, TimeoutException.class)) {
            type = ProviderErrorType.PROVIDER_TIMEOUT;
        } else if (causedBy(failure, UnknownHostException.class)
                || causedBy(failure, UnresolvedAddressException.class)) {
            type = ProviderErrorType.DNS_FAILED;
        } else if (causedBy(failure, SSLException.class)) {
            type = ProviderErrorType.TLS_FAILED;
        } else if (causedBy(failure, ConnectException.class)) {
            type = ProviderErrorType.CONNECTION_REFUSED;
        } else {
            type = ProviderErrorType.UNKNOWN;
        }
        return new ProviderCallException(
                type, null, null, latencyMillis, failure);
    }

    private boolean causedBy(
            Throwable failure, Class<? extends Throwable> expected) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (expected.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
