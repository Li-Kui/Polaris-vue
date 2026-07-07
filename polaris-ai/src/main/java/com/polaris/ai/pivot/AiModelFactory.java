package com.polaris.ai.pivot;

import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.service.IAiModelConfigService;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态多模型工厂类
 * 负责从数据库获取大模型与向量模型配置，动态组装并进行多实例本地缓存
 * 
 * @author polaris
 */
@Component
public class AiModelFactory
{
    private static final Logger log = LoggerFactory.getLogger(AiModelFactory.class);

    // 阿里通义 OpenAI 兼容地址
    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    // DeepSeek 官方地址
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";
    // 模型实例缓存，避免频繁从数据库查询及构建
    private final Map<String, StreamingChatModel> chatCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingCache = new ConcurrentHashMap<>();

    // 默认模型缓存，避免重复查询数据库
    private volatile StreamingChatModel defaultStreamingModel;
    private volatile EmbeddingModel defaultEmbeddingModel;
    @Autowired
    private IAiModelConfigService modelConfigService;
    @Autowired
    private AiModelProperties fileProps;

    /**
     * 清空全部大模型与向量模型缓存（模型配置增删改时调用）
     */
    public void clearCache()
    {
        log.info(">>> 清空动态大模型与向量模型缓存");
        chatCache.clear();
        embeddingCache.clear();
        defaultStreamingModel = null;
        defaultEmbeddingModel = null;
    }

    /**
     * 获取默认的聊天对话模型
     */
    public StreamingChatModel getDefaultStreamingModel()
    {
        if (defaultStreamingModel == null) {
            synchronized (this) {
                if (defaultStreamingModel == null) {
                    try {
                        AiModelConfig config = modelConfigService.selectDefaultChatModel();
                        if (config != null) {
                            defaultStreamingModel = getChatModelInstance(config);
                        }
                    } catch (Exception e) {
                        log.error("从数据库加载默认聊天模型失败，尝试回退到本地配置文件配置", e);
                    }
                    if (defaultStreamingModel == null) {
                        defaultStreamingModel = getFallbackChatModel();
                    }
                }
            }
        }
        return defaultStreamingModel;
    }

    /**
     * 根据模型名称动态获取聊天模型
     */
    public StreamingChatModel getStreamingModel(String modelName)
    {
        if (modelName == null || modelName.trim().isEmpty()) {
            return getDefaultStreamingModel();
        }

        try {
            AiModelConfig config = modelConfigService.selectModelConfigByModelName(modelName);
            if (config != null) {
                return getChatModelInstance(config);
            }
            log.warn(">>> 从数据库未检索到名称为 [{}] 且已启用的模型配置，将回退至默认聊天模型", modelName);
        } catch (Exception e) {
            log.error("从数据库检索模型 {} 失败，尝试回退", modelName, e);
        }
        
        // 若数据库中无此模型名称，且与文件配置相同，回退至文件默认
        if (modelName.equalsIgnoreCase(fileProps.getModelName())) {
            return getFallbackChatModel();
        }
        
        // 极限制兜底：如果完全查不到且非文件默认，尝试将当前传入名称作为默认模型的备用名重新获取
        return getDefaultStreamingModel();
    }

    /**
     * 获取默认的向量模型
     */
    public EmbeddingModel getEmbeddingModel()
    {
        if (defaultEmbeddingModel == null) {
            synchronized (this) {
                if (defaultEmbeddingModel == null) {
                    try {
                        AiModelConfig config = modelConfigService.selectDefaultEmbeddingModel();
                        if (config != null) {
                            defaultEmbeddingModel = getEmbeddingModelInstance(config);
                        }
                    } catch (Exception e) {
                        log.error("从数据库加载默认向量模型失败，回退到本地文件默认向量配置", e);
                    }
                    if (defaultEmbeddingModel == null) {
                        defaultEmbeddingModel = getFallbackEmbeddingModel();
                    }
                }
            }
        }
        return defaultEmbeddingModel;
    }

    // ================================================================
    //  内部构造与实例化逻辑
    // ================================================================

    private StreamingChatModel getChatModelInstance(AiModelConfig config)
    {
        String cacheKey = "chat_" + config.getId();
        return chatCache.computeIfAbsent(cacheKey, key -> {
            log.info(">>> 动态构建聊天模型, 名称={}, 提供商={}", config.getName(), config.getProvider());
            String provider = config.getProvider().toLowerCase();
            
            int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 2048;
            double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
            Map<String, String> customHeaders = getCustomHeaders(config);

            switch (provider) {
                case "dashscope":
                    String dashscopeUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                            ? config.getBaseUrl().trim() : DASHSCOPE_BASE_URL;
                    return OpenAiStreamingChatModel.builder()
                            .baseUrl(dashscopeUrl)
                            .apiKey(config.getApiKey())
                            .modelName(config.getModelName())
                            .maxTokens(maxTokens)
                            .temperature(temperature)
                            .timeout(Duration.ofSeconds(120))
                            .customHeaders(customHeaders)
                            .returnThinking("1".equals(config.getEnableThinking()))
                            .build();

                case "openai":
                    return OpenAiStreamingChatModel.builder()
                            .baseUrl(config.getBaseUrl()) // 支持配置自定义端点
                            .apiKey(config.getApiKey())
                            .modelName(config.getModelName())
                            .maxTokens(maxTokens)
                            .temperature(temperature)
                            .timeout(Duration.ofSeconds(120))
                            .customHeaders(customHeaders)
                            .returnThinking("1".equals(config.getEnableThinking()))
                            .build();

                case "deepseek":
                    return OpenAiStreamingChatModel.builder()
                            .baseUrl(DEEPSEEK_BASE_URL)
                            .apiKey(config.getApiKey())
                            .modelName(config.getModelName())
                            .maxTokens(maxTokens)
                            .temperature(temperature)
                            .timeout(Duration.ofSeconds(120))
                            .customHeaders(customHeaders)
                            .returnThinking("1".equals(config.getEnableThinking()))
                            .build();

                case "ollama":
                    String ollamaUrl = config.getBaseUrl() != null && !config.getBaseUrl().isEmpty() 
                            ? config.getBaseUrl() : "http://localhost:11434";
                    return OpenAiStreamingChatModel.builder()
                            .baseUrl(ollamaUrl + "/v1")
                            .apiKey("ollama")
                            .modelName(config.getModelName())
                            .temperature(temperature)
                            .timeout(Duration.ofSeconds(180))
                            .customHeaders(customHeaders)
                            .returnThinking("1".equals(config.getEnableThinking()))
                            .build();

                default:
                    throw new IllegalArgumentException("未知的 AI 提供商: " + provider);
            }
        });
    }

    private Map<String, String> getCustomHeaders(AiModelConfig config)
    {
        Map<String, String> headers = new java.util.HashMap<>();
        if ("1".equals(config.getEnableThinking())) {
            headers.put("X-Enable-Thinking", "true");
            if (config.getReasoningEffort() != null && !config.getReasoningEffort().trim().isEmpty()) {
                headers.put("X-Reasoning-Effort", config.getReasoningEffort());
            }
        }
        return headers;
    }

    private EmbeddingModel getEmbeddingModelInstance(AiModelConfig config)
    {
        String cacheKey = "embed_" + config.getId();
        return embeddingCache.computeIfAbsent(cacheKey, key -> {
            log.info(">>> 动态构建向量模型, 名称={}, 提供商={}", config.getName(), config.getProvider());
            String provider = config.getProvider().toLowerCase();
            String apiKey = config.getApiKey();

            switch (provider) {
                case "dashscope":
                    String dashscopeEmbedUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                            ? config.getBaseUrl().trim() : "https://dashscope.aliyuncs.com/compatible-mode/v1";
                    return OpenAiEmbeddingModel.builder()
                            .baseUrl(dashscopeEmbedUrl)
                            .apiKey(apiKey)
                            .modelName(config.getModelName())
                            .timeout(Duration.ofSeconds(60))
                            .build();

                case "openai":
                    return OpenAiEmbeddingModel.builder()
                            .baseUrl(config.getBaseUrl())
                            .apiKey(apiKey)
                            .modelName(config.getModelName())
                            .timeout(Duration.ofSeconds(60))
                            .build();

                case "deepseek":
                    log.warn("DeepSeek 暂无官方 Embedding 模型，默认配置阿里通义向量接口作为兜底");
                    return OpenAiEmbeddingModel.builder()
                            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                            .apiKey(apiKey)
                            .modelName("text-embedding-v3")
                            .timeout(Duration.ofSeconds(60))
                            .build();

                case "ollama":
                    String ollamaUrl = config.getBaseUrl() != null && !config.getBaseUrl().isEmpty() 
                            ? config.getBaseUrl() : "http://localhost:11434";
                    return OpenAiEmbeddingModel.builder()
                            .baseUrl(ollamaUrl + "/v1")
                            .apiKey("ollama")
                            .modelName(config.getModelName())
                            .timeout(Duration.ofSeconds(120))
                            .build();

                default:
                    throw new IllegalArgumentException("未知的向量模型提供商: " + provider);
            }
        });
    }

    // ================================================================
    //  极限制兜底：回退到本地 application.yml 配置
    // ================================================================

    private StreamingChatModel getFallbackChatModel()
    {
        log.warn(">>> 构建本地配置文件定义的默认聊天模型，provider={}", fileProps.getProvider());
        String provider = fileProps.getProvider().toLowerCase();
        
        switch (provider) {
            case "dashscope":
                return OpenAiStreamingChatModel.builder()
                        .baseUrl(DASHSCOPE_BASE_URL)
                        .apiKey(fileProps.getApiKey())
                        .modelName(fileProps.getModelName())
                        .maxTokens(fileProps.getMaxTokens())
                        .temperature(fileProps.getTemperature())
                        .timeout(Duration.ofSeconds(120))
                        .build();

            case "openai":
                return OpenAiStreamingChatModel.builder()
                        .apiKey(fileProps.getApiKey())
                        .modelName(fileProps.getModelName())
                        .maxTokens(fileProps.getMaxTokens())
                        .temperature(fileProps.getTemperature())
                        .timeout(Duration.ofSeconds(120))
                        .build();

            case "deepseek":
                return OpenAiStreamingChatModel.builder()
                        .baseUrl(DEEPSEEK_BASE_URL)
                        .apiKey(fileProps.getApiKey())
                        .modelName(fileProps.getModelName())
                        .maxTokens(fileProps.getMaxTokens())
                        .temperature(fileProps.getTemperature())
                        .timeout(Duration.ofSeconds(120))
                        .build();

            case "ollama":
                return OpenAiStreamingChatModel.builder()
                        .baseUrl(fileProps.getBaseUrl() + "/v1")
                        .apiKey("ollama")
                        .modelName(fileProps.getModelName())
                        .temperature(fileProps.getTemperature())
                        .timeout(Duration.ofSeconds(180))
                        .build();

            default:
                throw new IllegalArgumentException("不支持的 AI provider: " + provider);
        }
    }

    private EmbeddingModel getFallbackEmbeddingModel()
    {
        log.warn(">>> 构建本地配置文件定义的默认向量模型，provider={}", fileProps.getProvider());
        String provider = fileProps.getProvider().toLowerCase();
        String apiKey = fileProps.getApiKey();

        if ("dashscope".equalsIgnoreCase(provider) || "deepseek".equalsIgnoreCase(provider)) {
            return OpenAiEmbeddingModel.builder()
                    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                    .apiKey(apiKey)
                    .modelName("text-embedding-v3")
                    .timeout(Duration.ofSeconds(60))
                    .build();
        } else if ("openai".equalsIgnoreCase(provider)) {
            return OpenAiEmbeddingModel.builder()
                    .apiKey(apiKey)
                    .modelName("text-embedding-3-small")
                    .timeout(Duration.ofSeconds(60))
                    .build();
        } else {
            // ollama
            return OpenAiEmbeddingModel.builder()
                    .baseUrl(fileProps.getBaseUrl() + "/v1")
                    .apiKey("ollama")
                    .modelName("nomic-embed-text")
                    .timeout(Duration.ofSeconds(120))
                    .build();
        }
    }
}
