package com.polaris.ai.pivot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型统一配置属性
 * 对应 application.yml 中 ai.model.* 节点
 * 
 * @author polaris
 */
@Component
@ConfigurationProperties(prefix = "ai.model")
public class AiModelProperties 
{
    /**
     * 当前启用的提供商
     * 可选值: dashscope | openai | deepseek | ollama
     */
    private String provider = "dashscope";

    /** API Key（dashscope / openai / deepseek 使用） */
    private String apiKey;

    /**
     * 模型名称
     *  - dashscope : qwen-turbo / qwen-plus / qwen-max
     *  - openai    : gpt-4o / gpt-4o-mini
     *  - deepseek  : deepseek-chat / deepseek-reasoner
     *  - ollama    : qwen2.5 / llama3.2 / mistral ...
     */
    private String modelName = "qwen-plus";

    /** Ollama 专用：服务地址，默认 http://localhost:11434 */
    private String baseUrl = "http://localhost:11434";

    /** 回复最大 Token 数 */
    private Integer maxTokens = 2048;

    /** 随机温度 0~2，越高越随机 */
    private Double temperature = 0.7;

    /** 保留的历史消息条数（控制上下文窗口） */
    private Integer maxHistoryMessages = 20;

    /** 系统提示词（可在配置中自定义） */
    private String systemPrompt = "你是一个专业、友好的 AI 助手，请用中文回答问题。";

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public Integer getMaxTokens()
    {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens)
    {
        this.maxTokens = maxTokens;
    }

    public Double getTemperature()
    {
        return temperature;
    }

    public void setTemperature(Double temperature)
    {
        this.temperature = temperature;
    }

    public Integer getMaxHistoryMessages()
    {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(Integer maxHistoryMessages)
    {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public String getSystemPrompt()
    {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt)
    {
        this.systemPrompt = systemPrompt;
    }
}
