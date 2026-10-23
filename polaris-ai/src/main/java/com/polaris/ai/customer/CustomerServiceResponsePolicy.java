package com.polaris.ai.customer;

import com.polaris.ai.customer.application.CustomerServiceConfig;
import com.polaris.ai.modelcenter.runtime.ResolvedModelDefinition;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.common.exception.ServiceException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/** 客服发布固定的响应模式；只追加已核验的单次请求扩展，不改变共享模型或接收任意参数。 */
final class CustomerServiceResponsePolicy {
    private CustomerServiceResponsePolicy() { }

    static void validate(CustomerServiceConfig config, ResolvedModelDefinition model) {
        if (!"FAST".equals(config.responseMode())) return;
        var extra = model.providerExtraConfig();
        requireSupported(model.providerCode(), model.protocolCode(), model.networkMode(), model.modelName(),
                model.baseUrl(), extra != null && extra.isEmpty());
    }

    static ChatRequest apply(CustomerServiceConfig config, ModelRuntimeSpec runtime, ChatRequest request) {
        if (!"FAST".equals(config.responseMode())) return request;
        requireSupported(runtime.providerCode(), runtime.protocolCode(), runtime.networkMode(), runtime.modelName(),
                runtime.baseUrl(), runtime.providerExtraConfig().isEmpty());
        if (!"CHAT_COMPLETION".equals(runtime.capabilityCode()) || !Set.of("STREAMING").containsAll(runtime.activeFeatures())
                || request.toolSpecifications() != null && !request.toolSpecifications().isEmpty()) throw unavailable();
        return ChatRequest.builder().messages(request.messages()).parameters(OpenAiChatRequestParameters.builder()
                .customParameters(Map.of("enable_thinking", false)).build()).build();
    }

    private static void requireSupported(String provider, String protocol, String network, String model, String baseUrl, boolean noExtra) {
        if (!"DASHSCOPE".equals(provider) || !"OPENAI_COMPATIBLE".equals(protocol) || !"DIRECT".equals(network)
                || !"qwen3.8-27b".equals(model) || !noExtra || !officialEndpoint(baseUrl)) throw unavailable();
    }

    private static boolean officialEndpoint(String baseUrl) {
        try {
            var uri = URI.create(baseUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && "dashscope.aliyuncs.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && Set.of("/compatible-mode/v1", "/compatible-mode/v1/").contains(uri.getRawPath());
        } catch (RuntimeException invalid) { return false; }
    }

    private static ServiceException unavailable() {
        return new ServiceException("RESPONSE_MODE_UNAVAILABLE: 快速答复目前仅支持阿里云官方直连的 qwen3.8-27b，且不能包含连接扩展；请选择兼容模型或跟随模型模式", 409);
    }
}
