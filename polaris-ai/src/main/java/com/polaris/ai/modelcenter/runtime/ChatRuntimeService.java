package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.CapabilityExecutor;
import com.polaris.ai.runtime.ModelExecutionResult;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import com.polaris.ai.runtime.chat.ChatCapabilityInvocation;
import com.polaris.ai.runtime.stream.ModelStreamEvent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
