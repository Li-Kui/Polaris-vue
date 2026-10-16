package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.CapabilityExecutor;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.chat.ChatCapabilityInvocation;
import com.polaris.ai.runtime.stream.ModelStreamEvent;
import com.polaris.common.exception.ServiceException;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Flow;

/**
 * CHAT 新 Runtime 的内部入口。
 *
 * <p>现有 Direct Chat 的审核、平台配额、历史、工具和 SSE 仍位于业务层；
 * Phase 5A 只需将最终模型调用切换到本服务。</p>
 */
@Service
public class ChatRuntimeService {

    private static final String STREAMING = "STREAMING";

    private final ModelRuntimeResolver runtimeResolver;
    private final CapabilityExecutor capabilityExecutor;

    public ChatRuntimeService(
            ModelRuntimeResolver runtimeResolver,
            CapabilityExecutor capabilityExecutor) {
        this.runtimeResolver = runtimeResolver;
        this.capabilityExecutor = capabilityExecutor;
    }

    public ModelExecutionResult<ChatResponse> execute(
            Long modelId,
            ChatRequest request,
            Set<String> activeFeatures,
            List<CapabilityParameterOverride> overrides) {
        ChatCapabilityInvocation invocation =
                new ChatCapabilityInvocation(request);
        ModelRuntimeSpec runtime = runtimeResolver.resolve(
                new ModelRuntimeRequest<>(
                        modelId, invocation,
                        requestedFeatures(request, activeFeatures, false),
                        overrides));
        return capabilityExecutor.execute(
                runtime, invocation, ChatResponse.class);
    }

    public Flow.Publisher<ModelStreamEvent> stream(
            Long modelId,
            ChatRequest request,
            Set<String> activeFeatures,
            List<CapabilityParameterOverride> overrides) {
        ChatCapabilityInvocation invocation =
                new ChatCapabilityInvocation(request);
        ModelRuntimeSpec runtime = runtimeResolver.resolve(
                new ModelRuntimeRequest<>(
                        modelId, invocation,
                        requestedFeatures(request, activeFeatures, true),
                        overrides));
        return capabilityExecutor.stream(runtime, invocation);
    }

    /** 仅显式 QA 审阅使用：对已解析的单次 Runtime 检查后追加固定扩展，不写回模型配置。 */
    public Flow.Publisher<ModelStreamEvent> streamQaNonThinkingReview(
            Long modelId, ChatRequest request, String expectedDefinitionHash) {
        if (request == null || expectedDefinitionHash == null
                || !expectedDefinitionHash.matches("[a-f0-9]{64}")
                || request.messages() == null || request.messages().size() != 2
                || !(request.messages().get(0) instanceof SystemMessage)
                || !(request.messages().get(1) instanceof UserMessage user)
                || user.contents().stream().anyMatch(content -> !(content instanceof TextContent))
                || (request.toolSpecifications() != null && !request.toolSpecifications().isEmpty())) {
            throw qaModeUnavailable();
        }
        var invocation = new ChatCapabilityInvocation(request);
        ModelRuntimeSpec runtime = runtimeResolver.resolve(new ModelRuntimeRequest<>(
                modelId, invocation, Set.of(STREAMING), List.of()));
        if (!Objects.equals(modelId, runtime.modelId())
                || !Objects.equals(expectedDefinitionHash, runtime.runtimeDefinitionHash())
                || !"DASHSCOPE".equals(runtime.providerCode())
                || !"OPENAI_COMPATIBLE".equals(runtime.protocolCode())
                || !"DIRECT".equals(runtime.networkMode())
                || !"qwen3.8-27b".equals(runtime.modelName())
                || !"CHAT_COMPLETION".equals(runtime.capabilityCode())
                || !runtime.activeFeatures().equals(Set.of(STREAMING))
                || !runtime.providerExtraConfig().isEmpty()
                || !qaDashScopeEndpoint(runtime.baseUrl())) {
            throw qaModeUnavailable();
        }
        // 不接收任意 extra_body。仅本次 QA 请求传顶层 enable_thinking=false；常规模型默认参数由 SDK 合并。
        var selected = ChatRequest.builder().messages(request.messages())
                .parameters(OpenAiChatRequestParameters.builder()
                        .customParameters(Map.of("enable_thinking", false)).build()).build();
        return capabilityExecutor.stream(runtime, new ChatCapabilityInvocation(selected));
    }

    private boolean qaDashScopeEndpoint(String baseUrl) {
        try {
            var uri = URI.create(baseUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "dashscope.aliyuncs.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && Set.of("/compatible-mode/v1", "/compatible-mode/v1/").contains(uri.getRawPath());
        } catch (RuntimeException ignored) { return false; }
    }

    private ServiceException qaModeUnavailable() {
        return new ServiceException("QA_NON_THINKING_UNAVAILABLE: 模型、连接或运行时不满足已核验范围", 409);
    }

    /** SSE 是页面传输方式；未启用厂商流式能力时使用普通调用，仍保留能力校验。 */
    public Flow.Publisher<ModelStreamEvent> streamOrExecute(
            Long modelId, ChatRequest request, Set<String> activeFeatures,
            List<CapabilityParameterOverride> overrides, boolean streaming) {
        if (streaming) {
            return stream(modelId, request, activeFeatures, overrides);
        }
        return new BufferedChatPublisher(() -> execute(
                modelId, request, activeFeatures, overrides));
    }

    public void validateFeatures(
            ResolvedModelDefinition definition, ChatRequest request) {
        for (String feature : requestedFeatures(request, Set.of(), false)) {
            if (!definition.enabledFeatures().containsKey(feature)) {
                throw new com.polaris.common.exception.ServiceException(
                        "FEATURE_NOT_ENABLED: " + feature);
            }
        }
    }

    private Set<String> requestedFeatures(
            ChatRequest request,
            Set<String> explicitFeatures,
            boolean streaming) {
        Set<String> result = new LinkedHashSet<>(
                explicitFeatures == null ? Set.of() : explicitFeatures);
        result.removeIf(STREAMING::equalsIgnoreCase);
        if (streaming) {
            result.add(STREAMING);
        }
        if (request != null && request.toolSpecifications() != null
                && !request.toolSpecifications().isEmpty()) {
            result.add("TOOL_CALLING");
        }
        if (containsImage(request)) {
            result.add("VISION_INPUT");
        }
        return Set.copyOf(result);
    }

    private boolean containsImage(ChatRequest request) {
        if (request == null || request.messages() == null) {
            return false;
        }
        return request.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast)
                .flatMap(message -> message.contents().stream())
                .anyMatch(ImageContent.class::isInstance);
    }
}
