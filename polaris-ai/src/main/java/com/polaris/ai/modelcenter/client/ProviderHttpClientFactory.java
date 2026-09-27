package com.polaris.ai.modelcenter.client;

/** 按 Connection Revision 复用 Provider Client。 */
public interface ProviderHttpClientFactory {

    ProviderHttpClient get(ProviderRuntimeContext context);
}
