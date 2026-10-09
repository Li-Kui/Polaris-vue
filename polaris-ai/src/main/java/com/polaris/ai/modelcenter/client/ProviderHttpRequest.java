package com.polaris.ai.modelcenter.client;

import com.polaris.ai.modelcenter.protocol.ProtocolEndpoint;
import com.polaris.ai.modelcenter.protocol.ProtocolHttpMethod;

import java.time.Duration;
import java.util.Objects;

/** 只允许代码注册 ProtocolEndpoint 的受控 Provider 请求。 */
public record ProviderHttpRequest(
        ProtocolEndpoint endpoint,
        String jsonBody,
        Duration timeout,
        int maxRequestBytes,
        int maxResponseBytes) {

    public ProviderHttpRequest {
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Provider 请求超时必须在 0..5 分钟内");
        }
        if (maxRequestBytes < 0 || maxRequestBytes > 1_048_576
                || maxResponseBytes < 1 || maxResponseBytes > 4_194_304) {
            throw new IllegalArgumentException("Provider 请求/响应体限制无效");
        }
        if (endpoint.method() == ProtocolHttpMethod.GET
                && jsonBody != null && !jsonBody.isEmpty()) {
            throw new IllegalArgumentException("GET 不允许携带请求体");
        }
        if (endpoint.method() == ProtocolHttpMethod.POST && jsonBody == null) {
            throw new IllegalArgumentException("POST 必须携带 JSON 请求体");
        }
    }

    public static ProviderHttpRequest get(
            ProtocolEndpoint endpoint,
            Duration timeout,
            int maxResponseBytes) {
        return new ProviderHttpRequest(
                endpoint, null, timeout, 0, maxResponseBytes);
    }

    public static ProviderHttpRequest post(
            ProtocolEndpoint endpoint,
            String jsonBody,
            Duration timeout,
            int maxRequestBytes,
            int maxResponseBytes) {
        return new ProviderHttpRequest(
                endpoint, jsonBody, timeout, maxRequestBytes, maxResponseBytes);
    }

    @Override
    public String toString() {
        return "ProviderHttpRequest[endpoint=" + endpoint
                + ", jsonBody=<redacted>"
                + ", timeout=" + timeout
                + ", maxRequestBytes=" + maxRequestBytes
                + ", maxResponseBytes=" + maxResponseBytes + "]";
    }
}
