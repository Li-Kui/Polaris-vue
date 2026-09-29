package com.polaris.ai.pivot;

import com.polaris.ai.runtime.ModelHttpClientProperties;
import com.polaris.ai.runtime.ModelRuntimeSpec;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * 从请求级 {@link ModelRuntimeSpec} 创建协议客户端。
 *
 * <p>该工厂不查询模型主表、不选择默认模型、不缓存凭据；模型定义、连接、
 * 能力与运行策略均由统一 Runtime Resolver 在调用前完成解析。</p>
 */
@Component
public class AiModelFactory {

    private static final String OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    private final ModelHttpClientProperties httpProperties;

    /** 兼容不启动 Spring 容器的运行时单元测试。 */
    public AiModelFactory() {
        this(new ModelHttpClientProperties());
    }

    @Autowired
    public AiModelFactory(ModelHttpClientProperties httpProperties) {
        this.httpProperties = httpProperties;
    }

    public ChatModel createChatModel(ModelRuntimeSpec runtime) {
        RuntimeChatSettings settings = runtimeChatSettings(runtime);
        OpenAiChatModel.OpenAiChatModelBuilder builder =
                OpenAiChatModel.builder()
                        .httpClientBuilder(httpClientBuilder())
                        .baseUrl(settings.baseUrl())
                        .apiKey(settings.apiKey())
                        .modelName(settings.modelName())
                        .temperature(settings.temperature())
                        .timeout(settings.timeout())
                        .maxRetries(0)
                        .logRequests(false)
                        .logResponses(false);
        if (settings.maxTokens() != null) {
            builder.maxTokens(settings.maxTokens());
        }
        applyReasoning(runtime, builder);
        return builder.build();
    }

    public StreamingChatModel createStreamingChatModel(
            ModelRuntimeSpec runtime) {
        RuntimeChatSettings settings = runtimeChatSettings(runtime);
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder =
                OpenAiStreamingChatModel.builder()
                        .httpClientBuilder(httpClientBuilder())
                        .baseUrl(settings.baseUrl())
                        .apiKey(settings.apiKey())
                        .modelName(settings.modelName())
                        .temperature(settings.temperature())
                        .timeout(settings.timeout())
                        .logRequests(false)
                        .logResponses(false);
        if (settings.maxTokens() != null) {
            builder.maxTokens(settings.maxTokens());
        }
        applyReasoning(runtime, builder);
        return builder.build();
    }

    public EmbeddingModel createEmbeddingModel(ModelRuntimeSpec runtime) {
        Objects.requireNonNull(runtime, "runtime");
        requireCapability(runtime, "TEXT_EMBEDDING");
        requireOpenAiCompatible(runtime, "Embedding");
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder =
                OpenAiEmbeddingModel.builder()
                        .httpClientBuilder(httpClientBuilder())
                        .baseUrl(requiredString(runtime.baseUrl(), "baseUrl"))
                        .apiKey(requiredString(
                                runtime.credentials().get("apiKey"), "apiKey"))
                        .modelName(requiredString(
                                runtime.modelName(), "modelName"))
                        .timeout(Duration.ofMillis(
                                runtime.runtimePolicy().readTimeoutMs()))
                        .maxRetries(0)
                        .logRequests(false)
                        .logResponses(false);
        String dimensionMode = requiredString(
                runtime.invocationParameters().get("dimensionMode"),
                "dimensionMode");
        Integer dimension = optionalInteger(
                runtime.invocationParameters().get("dimension"),
                "dimension", 1, Integer.MAX_VALUE);
        if ("REQUEST".equalsIgnoreCase(dimensionMode)) {
            if (dimension == null) {
                throw new IllegalArgumentException(
                        "REQUEST dimensionMode 必须提供 dimension");
            }
            builder.dimensions(dimension);
        }
        Integer batchSize = optionalInteger(
                runtime.invocationParameters().get("batchSize"),
                "batchSize", 1, 2048);
        if (batchSize != null) {
            builder.maxSegmentsPerBatch(batchSize);
        }
        return builder.build();
    }

    private JdkHttpClientBuilder httpClientBuilder() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        httpProperties.applyProxy(builder);
        return new JdkHttpClientBuilder().httpClientBuilder(builder);
    }

    private RuntimeChatSettings runtimeChatSettings(ModelRuntimeSpec runtime) {
        Objects.requireNonNull(runtime, "runtime");
        requireCapability(runtime, "CHAT_COMPLETION");
        requireOpenAiCompatible(runtime, "Chat");
        return new RuntimeChatSettings(
                requiredString(runtime.baseUrl(), "baseUrl"),
                requiredString(runtime.credentials().get("apiKey"), "apiKey"),
                requiredString(runtime.modelName(), "modelName"),
                optionalInteger(runtime.invocationParameters().get("maxTokens"),
                        "maxTokens", 1, 200_000),
                optionalDouble(runtime.invocationParameters().get("temperature"),
                        "temperature", 0D, 2D),
                Duration.ofMillis(runtime.runtimePolicy().readTimeoutMs()));
    }

    private void requireCapability(
            ModelRuntimeSpec runtime, String capabilityCode) {
        if (!capabilityCode.equals(runtime.capabilityCode())) {
            throw new IllegalArgumentException(
                    "Runtime Capability 不是 " + capabilityCode);
        }
    }

    private void requireOpenAiCompatible(
            ModelRuntimeSpec runtime, String capabilityName) {
        if (!OPENAI_COMPATIBLE.equals(runtime.protocolCode())) {
            throw new IllegalArgumentException(
                    "暂不支持的 " + capabilityName + " Protocol: "
                            + runtime.protocolCode());
        }
    }

    private void applyReasoning(
            ModelRuntimeSpec runtime,
            OpenAiChatModel.OpenAiChatModelBuilder builder) {
        if (!runtime.activeFeatures().contains("REASONING")) {
            return;
        }
        builder.returnThinking(true);
        Object effort = runtime.featureParameters()
                .getOrDefault("REASONING", Map.of()).get("effort");
        if (effort instanceof String value && !value.isBlank()) {
            builder.reasoningEffort(value);
        }
    }

    private void applyReasoning(
            ModelRuntimeSpec runtime,
            OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder) {
        if (!runtime.activeFeatures().contains("REASONING")) {
            return;
        }
        builder.returnThinking(true);
        Object effort = runtime.featureParameters()
                .getOrDefault("REASONING", Map.of()).get("effort");
        if (effort instanceof String value && !value.isBlank()) {
            builder.reasoningEffort(value);
        }
    }

    private String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Runtime " + field + " 不能为空");
        }
        return text.trim();
    }

    private Integer optionalInteger(
            Object value, String field, int minimum, int maximum) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Runtime " + field + " 类型无效");
        }
        double raw = number.doubleValue();
        int result = number.intValue();
        if (!Double.isFinite(raw) || raw != result
                || result < minimum || result > maximum) {
            throw new IllegalArgumentException(
                    "Runtime " + field + " 数值无效");
        }
        return result;
    }

    private Double optionalDouble(
            Object value, String field, double minimum, double maximum) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Runtime " + field + " 类型无效");
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result)
                || result < minimum || result > maximum) {
            throw new IllegalArgumentException(
                    "Runtime " + field + " 数值无效");
        }
        return result;
    }

    private record RuntimeChatSettings(
            String baseUrl,
            String apiKey,
            String modelName,
            Integer maxTokens,
            Double temperature,
            Duration timeout) {
    }
}
