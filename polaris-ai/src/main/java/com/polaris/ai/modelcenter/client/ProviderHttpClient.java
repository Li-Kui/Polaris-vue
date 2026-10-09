package com.polaris.ai.modelcenter.client;

/** 统一 Provider HTTP Client，只接受受控 ProtocolEndpoint 请求。 */
public interface ProviderHttpClient {

    ProviderHttpResponse execute(ProviderHttpRequest request);
}
