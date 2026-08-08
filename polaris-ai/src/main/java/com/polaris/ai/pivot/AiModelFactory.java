package com.polaris.ai.pivot;

import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.enums.ModelType;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.common.core.domain.entity.SysRole;
import com.polaris.common.core.domain.entity.SysUser;
import com.polaris.common.utils.SecurityUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态多模型工厂类
 * 负责从数据库获取大模型与向量模型配置，动态组装并进行多实例本地缓存
 * 
 * @author polaris
 */
@Slf4j
@Component
public class AiModelFactory
{

    private static final String EMBEDDING_DIMENSION_PROBE_TEXT = "Polaris embedding dimension probe";
    // 阿里通义 OpenAI 兼容地址
    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    // DeepSeek 官方地址
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";
    // 模型实例缓存，避免频繁从数据库查询及构建
    private final Map<String, StreamingChatModel> chatCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingCache = new ConcurrentHashMap<>();
    private final Map<String, ImageModel> imageCache = new ConcurrentHashMap<>();
    // 配置对象缓存，用以实现全流程零数据库 I/O
    private final Map<Long, AiModelConfig> configCache = new ConcurrentHashMap<>();

    // 默认模型缓存从单例变量重构为部门隔离多实例映射，支持并发安全
    private final Map<String, StreamingChatModel> defaultChatCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> defaultEmbeddingCache = new ConcurrentHashMap<>();
    private final Map<String, ImageModel> defaultImageCache = new ConcurrentHashMap<>();

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
        imageCache.clear();
        configCache.clear();
        defaultChatCache.clear();
        defaultEmbeddingCache.clear();
        defaultImageCache.clear();
    }

    /**
     * 清除指定模型配置的所有缓存（模型配置更新/删除时调用）
     */
    public void evictCache(Long configId) {
        if (configId == null) return;
        chatCache.remove("chat_" + configId);
        embeddingCache.remove("embed_" + configId);
        imageCache.remove("image_" + configId);
        log.info(">>> 已清除模型配置缓存, configId={}", configId);
    }

    /**
     * 编程式动态拼接角色关联的数据权限 SQL 过滤片段
     */
    private String buildDataScopeSql(SysUser user, String deptAlias, String deptField)
    {
        if (user == null) {
            return " AND " + deptAlias + "." + deptField + " IS NULL"; // 未登录或无上下文只看全局共享
        }
        if (user.isAdmin()) {
            return ""; // 超级管理员直接返回空，拥有一切权限
        }

        StringBuilder sqlString = new StringBuilder();
        List<String> conditions = new ArrayList<>();
        List<String> scopeCustomIds = new ArrayList<>();
        
        user.getRoles().forEach(role -> {
            if ("2".equals(role.getDataScope()) && "0".equals(role.getStatus())) {
                scopeCustomIds.add(String.valueOf(role.getRoleId()));
            }
        });

        for (SysRole role : user.getRoles()) {
            String dataScope = role.getDataScope();
            if (conditions.contains(dataScope) || "1".equals(role.getStatus())) {
                continue;
            }
            if ("1".equals(dataScope)) { // 全部数据权限
                sqlString = new StringBuilder();
                conditions.add(dataScope);
                break;
            } else if ("2".equals(dataScope)) { // 自定义数据权限
                if (scopeCustomIds.size() > 1) {
                    sqlString.append(String.format(" OR %s.%s IN ( SELECT dept_id FROM sys_role_dept WHERE role_id in (%s) ) ", deptAlias, deptField, String.join(",", scopeCustomIds)));
                } else {
                    sqlString.append(String.format(" OR %s.%s IN ( SELECT dept_id FROM sys_role_dept WHERE role_id = %d ) ", deptAlias, deptField, role.getRoleId()));
                }
            } else if ("3".equals(dataScope)) { // 本部门数据权限
                sqlString.append(String.format(" OR %s.%s = %d ", deptAlias, deptField, user.getDeptId()));
            } else if ("4".equals(dataScope)) { // 本部门及以下数据权限
                sqlString.append(String.format(" OR %s.%s IN ( SELECT dept_id FROM sys_dept WHERE dept_id = %d or find_in_set( %d , ancestors ) )", deptAlias, deptField, user.getDeptId(), user.getDeptId()));
            } else if ("5".equals(dataScope)) { // 仅本人数据权限
                sqlString.append(String.format(" OR %s.%s = 0 ", deptAlias, deptField));
            }
            conditions.add(dataScope);
        }

        if (conditions.isEmpty()) {
            return " AND " + deptAlias + "." + deptField + " = 0";
        }

        if (sqlString.length() > 0) {
            // 将全局共享模型(dept_id IS NULL)融入数据权限白名单中
            return " AND (" + sqlString.substring(4) + " OR " + deptAlias + "." + deptField + " IS NULL)";
        }
        
        return "";
    }

    /**
     * 统一获取对应用途下有权访问的默认模型配置对象
     */
    public AiModelConfig getDefaultModelConfig(ModelType type)
    {
        SysUser user = null;
        Long userDeptId = null;
        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                user = SecurityUtils.getLoginUser().getUser();
                userDeptId = user.getDeptId();
            }
        } catch (Exception e) {
            // 无Web登录上下文
        }

        String dataScopeSql = buildDataScopeSql(user, "ai_model_config", "dept_id");
        try {
            return modelConfigService.selectDefaultModel(type.name(), userDeptId, dataScopeSql);
        } catch (Exception e) {
            log.error("从数据库加载默认 [{}] 模型配置失败，userDeptId={}", type.name(), userDeptId, e);
        }
        return null;
    }

    /**
     * 获取默认的聊天对话模型
     */
    public StreamingChatModel getDefaultStreamingModel()
    {
        SysUser user = null;
        Long userDeptId = null;
        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                user = SecurityUtils.getLoginUser().getUser();
                userDeptId = user.getDeptId();
            }
        } catch (Exception e) {
            // 正常捕获，说明无Web请求登录上下文
        }

        String cacheKey = "default_chat_" + (user != null && user.isAdmin() ? "admin" : (userDeptId != null ? userDeptId : "global"));
        
        StreamingChatModel cachedModel = defaultChatCache.get(cacheKey);
        if (cachedModel != null) {
            return cachedModel;
        }

        synchronized (this) {
            cachedModel = defaultChatCache.get(cacheKey);
            if (cachedModel != null) {
                return cachedModel;
            }

            try {
                AiModelConfig config = getDefaultModelConfig(ModelType.CHAT);
                if (config != null) {
                    cachedModel = getChatModelInstance(config);
                    defaultChatCache.put(cacheKey, cachedModel);
                    return cachedModel;
                }
            } catch (Exception e) {
                log.error("从数据库加载角色数据权限关联的默认聊天模型失败，userDeptId={}", userDeptId, e);
            }

            if (cachedModel == null) {
                cachedModel = getFallbackChatModel();
                defaultChatCache.put(cacheKey, cachedModel);
            }
        }
        return cachedModel;
    }

    /**
     * 获取当前有权访问的默认聊天对话模型配置对象
     */
    public AiModelConfig getDefaultChatModelConfig()
    {
        return getDefaultModelConfig(ModelType.CHAT);
    }

    /**
     * 获取大模型配置对象（带本地 ConcurrentHashMap 缓存，达成 0 物理库查询）
     *
     * @param modelConfigId 模型配置ID
     * @return 缓存中或新加载的模型配置，为 null 时代表参数无效
     */
    public AiModelConfig getModelConfig(Long modelConfigId)
    {
        if (modelConfigId == null) {
            return null;
        }
        return configCache.computeIfAbsent(modelConfigId, id -> {
            log.info(">>> [AiModelFactory] 首次加载模型配置对象并装载本地内存缓存, ID={}", id);
            return modelConfigService.selectModelConfigById(id);
        });
    }

    /**
     * 根据模型配置 ID 动态获取聊天模型（带本地缓存，实现 0 次数据库查询）
     *
     * @param modelConfigId 模型配置ID
     * @return 聊天模型实例
     */
    public StreamingChatModel getStreamingModel(Long modelConfigId)
    {
        if (modelConfigId == null) {
            return getDefaultStreamingModel();
        }

        String cacheKey = "chat_" + modelConfigId;
        StreamingChatModel cachedModel = chatCache.get(cacheKey);
        if (cachedModel != null) {
            return cachedModel;
        }

        synchronized (this) {
            cachedModel = chatCache.get(cacheKey);
            if (cachedModel != null) {
                return cachedModel;
            }

            try {
                AiModelConfig config = getModelConfig(modelConfigId);
                if (config != null) {
                    return getChatModelInstance(config);
                }
                log.warn(">>> 从数据库未检索到主键ID为 [{}] 的模型配置，将回退至默认聊天模型", modelConfigId);
            } catch (Exception e) {
                log.error("从数据库检索模型ID {} 失败，尝试回退", modelConfigId, e);
            }
        }

        return getDefaultStreamingModel();
    }

    /**
     * 获取默认的向量模型
     */
    public EmbeddingModel getEmbeddingModel()
    {
        SysUser user = null;
        Long userDeptId = null;
        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                user = SecurityUtils.getLoginUser().getUser();
                userDeptId = user.getDeptId();
            }
        } catch (Exception e) {
            // 正常捕获，无Web登录上下文
        }

        String cacheKey = "default_embed_" + (user != null && user.isAdmin() ? "admin" : (userDeptId != null ? userDeptId : "global"));
        
        EmbeddingModel cachedModel = defaultEmbeddingCache.get(cacheKey);
        if (cachedModel != null) {
            return cachedModel;
        }

        synchronized (this) {
            cachedModel = defaultEmbeddingCache.get(cacheKey);
            if (cachedModel != null) {
                return cachedModel;
            }

            try {
                AiModelConfig config = getDefaultModelConfig(ModelType.EMBEDDING);
                if (config != null) {
                    cachedModel = getEmbeddingModelInstance(config);
                    defaultEmbeddingCache.put(cacheKey, cachedModel);
                    return cachedModel;
                }
            } catch (Exception e) {
                log.error("从数据库加载角色数据权限关联的默认向量模型失败，userDeptId={}", userDeptId, e);
            }

            if (cachedModel == null) {
                cachedModel = getFallbackEmbeddingModel();
                defaultEmbeddingCache.put(cacheKey, cachedModel);
            }
        }
        return cachedModel;
    }

    /**
     * 按明确的模型配置 ID 获取向量模型。知识库索引必须使用该方法，
     * 避免异步线程和查询线程因安全上下文不同而选中不同模型。
     */
    public EmbeddingModel getEmbeddingModel(Long modelConfigId)
    {
        if (modelConfigId == null) {
            return getEmbeddingModel();
        }
        AiModelConfig config = getModelConfig(modelConfigId);
        if (config == null) {
            throw new IllegalArgumentException("向量模型配置不存在: " + modelConfigId);
        }
        if (!ModelType.EMBEDDING.name().equalsIgnoreCase(config.getModelType())) {
            throw new IllegalArgumentException("模型配置不是 EMBEDDING 类型: " + modelConfigId);
        }
        if (!"1".equals(config.getStatus())) {
            throw new IllegalArgumentException("向量模型配置未启用: " + modelConfigId);
        }
        return getEmbeddingModelInstance(config);
    }

    /**
     * Returns the configured embedding dimension, probing and persisting it on first use when needed.
     */
    public int ensureEmbeddingDimension(AiModelConfig config)
    {
        if (config != null && config.getEmbeddingDimension() != null
                && config.getEmbeddingDimension() > 0) {
            return config.getEmbeddingDimension();
        }
        return probeEmbeddingDimension(config);
    }

    /**
     * Verifies the embedding endpoint and persists the provider's actual output dimension.
     */
    public synchronized int probeEmbeddingDimension(AiModelConfig config)
    {
        if (config == null || config.getId() == null) {
            throw new IllegalArgumentException("向量模型配置不能为空");
        }
        if (!ModelType.EMBEDDING.name().equalsIgnoreCase(config.getModelType())) {
            throw new IllegalArgumentException("模型配置不是 EMBEDDING 类型: " + config.getId());
        }
        if (!"1".equals(config.getStatus())) {
            throw new IllegalArgumentException("向量模型配置未启用: " + config.getId());
        }

        int actualDimension = getEmbeddingModel(config.getId())
                .embed(EMBEDDING_DIMENSION_PROBE_TEXT)
                .content()
                .dimension();
        if (actualDimension <= 0) {
            throw new IllegalStateException("向量模型返回了无效维度: " + actualDimension);
        }
        if (config.getEmbeddingDimension() != null
                && config.getEmbeddingDimension() != actualDimension) {
            throw new IllegalStateException("向量模型维度不匹配：配置="
                    + config.getEmbeddingDimension() + "，实际=" + actualDimension);
        }
        if (config.getEmbeddingDimension() == null) {
            AiModelConfig dimensionUpdate = new AiModelConfig();
            dimensionUpdate.setId(config.getId());
            dimensionUpdate.setEmbeddingDimension(actualDimension);
            if (modelConfigService.updateModelConfig(dimensionUpdate) <= 0) {
                throw new IllegalStateException("向量模型维度写入数据库失败: " + config.getId());
            }
            config.setEmbeddingDimension(actualDimension);
            AiModelConfig cachedConfig = configCache.get(config.getId());
            if (cachedConfig != null) {
                cachedConfig.setEmbeddingDimension(actualDimension);
            }
            log.info(">>> 向量模型维度探测完成并持久化, configId={}, dimension={}",
                    config.getId(), actualDimension);
        }
        return actualDimension;
    }

    /**
     * 按指定模型配置获取图像模型实例（带缓存），供绘图适配器调用
     */
    public ImageModel getImageModel(AiModelConfig config)
    {
        if (config == null) {
            return null;
        }
        return getImageModelInstance(config);
    }

    /**
     * 获取默认的图像生成模型
     */
    public ImageModel getDefaultImageModel()
    {
        SysUser user = null;
        Long userDeptId = null;
        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                user = SecurityUtils.getLoginUser().getUser();
                userDeptId = user.getDeptId();
            }
        } catch (Exception e) {
            // 无Web登录上下文
        }

        String cacheKey = "default_image_" + (user != null && user.isAdmin() ? "admin" : (userDeptId != null ? userDeptId : "global"));
        
        ImageModel cachedModel = defaultImageCache.get(cacheKey);
        if (cachedModel != null) {
            return cachedModel;
        }

        synchronized (this) {
            cachedModel = defaultImageCache.get(cacheKey);
            if (cachedModel != null) {
                return cachedModel;
            }

            try {
                AiModelConfig config = getDefaultModelConfig(ModelType.IMAGE);
                if (config != null) {
                    cachedModel = getImageModelInstance(config);
                    defaultImageCache.put(cacheKey, cachedModel);
                    return cachedModel;
                }
            } catch (Exception e) {
                log.error("从数据库加载角色数据权限关联的默认图像生成模型失败，userDeptId={}", userDeptId, e);
            }
        }
        return cachedModel;
    }

    // ================================================================
    //  内部构造与实例化逻辑
    // ================================================================

    private StreamingChatModel getChatModelInstance(AiModelConfig config)
    {
        String cacheKey = "chat_" + config.getId();
        return getChatModelInstance(config, cacheKey);
    }

    /** 使用执行快照参数构建工作流模型；缓存键包含快照指纹，避免复用同 ID 的新配置。 */
    public StreamingChatModel getWorkflowStreamingModel(AiModelConfig config, String snapshotFingerprint)
    {
        return getWorkflowStreamingModel(config, snapshotFingerprint, null);
    }

    public StreamingChatModel getWorkflowStreamingModel(
            AiModelConfig config, String snapshotFingerprint, Duration requestTimeout)
    {
        if (config == null || config.getId() == null) {
            throw new IllegalArgumentException("工作流模型快照不完整");
        }
        String cacheKey = "workflow_chat_" + config.getId() + "_" + snapshotFingerprint;
        return getChatModelInstance(config, cacheKey, requestTimeout);
    }

    private StreamingChatModel getChatModelInstance(AiModelConfig config, String cacheKey)
    {
        return getChatModelInstance(config, cacheKey, null);
    }

    private StreamingChatModel getChatModelInstance(
            AiModelConfig config, String cacheKey, Duration requestTimeout)
    {
        return chatCache.computeIfAbsent(cacheKey, key -> {
            log.info(">>> 动态构建聊天模型, 名称={}, 提供商={}", config.getName(), config.getProvider());
            String provider = config.getProvider().toLowerCase();
            
            int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 2048;
            double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
            Map<String, String> customHeaders = getCustomHeaders(config);

            // ── 中转站模式：统一用 OpenAI 兼容协议 ──
            if (config.isRelay()) {
                log.info(">>> [中转站模式] 使用 OpenAI 兼容协议构建聊天模型");
                String relayUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                        ? config.getBaseUrl().trim() : null;
                if (relayUrl == null) {
                    throw new IllegalArgumentException("中转站模式下必须填写 API Base URL");
                }
                return OpenAiStreamingChatModel.builder()
                        .baseUrl(relayUrl)
                        .apiKey(config.getApiKey())
                        .modelName(config.getModelName())
                        .maxTokens(maxTokens)
                        .temperature(temperature)
                        .timeout(timeoutOrDefault(requestTimeout, 120))
                        .customHeaders(customHeaders)
                        .returnThinking("1".equals(config.getEnableThinking()))
                        .build();
            }

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
                            .timeout(timeoutOrDefault(requestTimeout, 120))
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
                            .timeout(timeoutOrDefault(requestTimeout, 120))
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
                            .timeout(timeoutOrDefault(requestTimeout, 120))
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
                            .timeout(timeoutOrDefault(requestTimeout, 180))
                            .customHeaders(customHeaders)
                            .returnThinking("1".equals(config.getEnableThinking()))
                            .build();

                case "ark":
                    String arkUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                            ? config.getBaseUrl().trim() : "https://ark.cn-beijing.volces.com/api/v3";
                    return OpenAiStreamingChatModel.builder()
                            .baseUrl(arkUrl)
                            .apiKey(config.getApiKey())
                            .modelName(config.getModelName())
                            .maxTokens(maxTokens)
                            .temperature(temperature)
                            .timeout(timeoutOrDefault(requestTimeout, 120))
                            .customHeaders(customHeaders)
                            .returnThinking("1".equals(config.getEnableThinking()))
                            .build();

                default:
                    throw new IllegalArgumentException("未知的 AI 提供商: " + provider);
            }
        });
    }

    private Duration timeoutOrDefault(Duration requestTimeout, long defaultSeconds)
    {
        return requestTimeout == null ? Duration.ofSeconds(defaultSeconds) : requestTimeout;
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

            // ── 中转站模式：统一用 OpenAI 兼容协议 ──
            if (config.isRelay()) {
                log.info(">>> [中转站模式] 使用 OpenAI 兼容协议构建向量模型");
                String relayUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                        ? config.getBaseUrl().trim() : null;
                if (relayUrl == null) {
                    throw new IllegalArgumentException("中转站模式下必须填写 API Base URL");
                }
                return buildOpenAiEmbeddingModel(config, relayUrl, apiKey, config.getModelName(), Duration.ofSeconds(60));
            }

            switch (provider) {
                case "dashscope":
                    String dashscopeEmbedUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                            ? config.getBaseUrl().trim() : "https://dashscope.aliyuncs.com/compatible-mode/v1";
                    return buildOpenAiEmbeddingModel(
                            config, dashscopeEmbedUrl, apiKey, config.getModelName(), Duration.ofSeconds(60));

                case "openai":
                    return buildOpenAiEmbeddingModel(
                            config, config.getBaseUrl(), apiKey, config.getModelName(), Duration.ofSeconds(60));

                case "deepseek":
                    log.warn("DeepSeek 暂无官方 Embedding 模型，默认配置阿里通义向量接口作为兜底");
                    return buildOpenAiEmbeddingModel(
                            config,
                            "https://dashscope.aliyuncs.com/compatible-mode/v1",
                            apiKey,
                            "text-embedding-v3",
                            Duration.ofSeconds(60));

                case "ollama":
                    String ollamaUrl = config.getBaseUrl() != null && !config.getBaseUrl().isEmpty() 
                            ? config.getBaseUrl() : "http://localhost:11434";
                    return buildOpenAiEmbeddingModel(
                            config, ollamaUrl + "/v1", "ollama", config.getModelName(), Duration.ofSeconds(120));

                case "ark":
                    String arkEmbedUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                            ? config.getBaseUrl().trim() : "https://ark.cn-beijing.volces.com/api/v3";
                    return buildOpenAiEmbeddingModel(
                            config, arkEmbedUrl, apiKey, config.getModelName(), Duration.ofSeconds(60));

                default:
                    throw new IllegalArgumentException("未知的向量模型提供商: " + provider);
            }
        });
    }

    private EmbeddingModel buildOpenAiEmbeddingModel(
            AiModelConfig config,
            String baseUrl,
            String apiKey,
            String modelName,
            Duration timeout)
    {
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(timeout);
        if ("REQUEST".equalsIgnoreCase(config.getEmbeddingDimensionMode())
                && config.getEmbeddingDimension() != null) {
            builder.dimensions(config.getEmbeddingDimension());
        }
        if (config.getEmbeddingBatchSize() != null) {
            builder.maxSegmentsPerBatch(config.getEmbeddingBatchSize());
        }
        return builder.build();
    }

    private ImageModel getImageModelInstance(AiModelConfig config)
    {
        String cacheKey = "image_" + config.getId();
        return imageCache.computeIfAbsent(cacheKey, key -> {
            log.info(">>> 动态构建图像生成模型, 名称={}, 提供商={}", config.getName(), config.getProvider());
            String provider = config.getProvider().toLowerCase();
            String apiKey = config.getApiKey();

            // ── 中转站模式：统一用 OpenAI 兼容协议 ──
            if (config.isRelay()) {
                log.info(">>> [中转站模式] 使用 OpenAI 兼容协议构建图像模型");
                return OpenAiImageModel.builder()
                        .baseUrl(config.getBaseUrl())
                        .apiKey(apiKey)
                        .modelName(config.getModelName())
                        .timeout(Duration.ofSeconds(60))
                        .build();
            }

            switch (provider) {
                case "dashscope":
                    String dashscopeImageUrl = config.getBaseUrl() != null && !config.getBaseUrl().trim().isEmpty()
                            ? config.getBaseUrl().trim() : DASHSCOPE_BASE_URL;
                    return OpenAiImageModel.builder()
                            .baseUrl(dashscopeImageUrl)
                            .apiKey(apiKey)
                            .modelName(config.getModelName() != null && !config.getModelName().isEmpty() ? config.getModelName() : "wanx-v1")
                            .timeout(Duration.ofSeconds(60))
                            .build();

                case "openai":
                    return OpenAiImageModel.builder()
                            .baseUrl(config.getBaseUrl())
                            .apiKey(apiKey)
                            .modelName(config.getModelName() != null && !config.getModelName().isEmpty() ? config.getModelName() : "dall-e-3")
                            .timeout(Duration.ofSeconds(60))
                            .build();

                default:
                    return OpenAiImageModel.builder()
                            .baseUrl(config.getBaseUrl())
                            .apiKey(apiKey)
                            .modelName(config.getModelName())
                            .timeout(Duration.ofSeconds(60))
                            .build();
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

            case "ark":
                return OpenAiStreamingChatModel.builder()
                        .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                        .apiKey(fileProps.getApiKey())
                        .modelName(fileProps.getModelName())
                        .maxTokens(fileProps.getMaxTokens())
                        .temperature(fileProps.getTemperature())
                        .timeout(Duration.ofSeconds(120))
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
