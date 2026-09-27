package com.polaris.ai.modelcenter.client;

/** Provider HTTP 响应；toString 不回显模型输出或错误体。 */
public record ProviderHttpResponse(
        int statusCode,
        String body,
        String providerRequestId,
        long latencyMillis) {

    @Override
    public String toString() {
        return "ProviderHttpResponse[statusCode=" + statusCode
                + ", body=<redacted>"
                + ", providerRequestId=" + providerRequestId
                + ", latencyMillis=" + latencyMillis + "]";
    }
}
