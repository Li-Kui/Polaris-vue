package com.polaris.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.dto.FetchModelsRequest;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import com.polaris.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 模型配置控制器
 *
 * @author polaris
 */
@Slf4j
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "AI模型管理")
@RestController
@RequestMapping("/ai/model")
public class AiModelConfigController extends BaseController {

    /** 阿里通义 OpenAI 兼容地址 */
    private static final String DASHSCOPE_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    /** DeepSeek 官方地址 */
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";
    /** 火山引擎 Ark 默认地址 */
    private static final String ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    /** Ollama 默认地址 */
    private static final String OLLAMA_DEFAULT_URL = "http://localhost:11434";
    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private AiModelFactory modelFactory;

    /**
     * 查询模型配置列表
     */
    @Operation(summary = "查询模型配置列表")
    @GetMapping("/list")
    public ResultData<Page<AiModelConfig>> list(AiModelConfig config) {
        startPage();
        List<AiModelConfig> list = modelConfigService.selectModelConfigList(config);
        return ok(getDataPage(list));
    }

    /**
     * 查询当前登录用户可用的模型列表（系统共享 + 指定部门独享）
     */
    @Operation(summary = "查询当前用户可用的大模型列表")
    @GetMapping("/list/available")
    public ResultData<List<AiModelConfig>> listAvailable() {
        Long userId = SecurityUtils.getUserId();
        boolean isAdmin = SecurityUtils.isAdmin(userId);
        Long deptId = null;
        if (!isAdmin) {
            if (SecurityUtils.getLoginUser() != null && SecurityUtils.getLoginUser().getUser() != null) {
                deptId = SecurityUtils.getLoginUser().getUser().getDeptId();
            }
        }
        List<AiModelConfig> list = modelConfigService.selectAvailableModelConfigs(deptId, isAdmin);
        return ok(list);
    }

    /**
     * 获取模型配置详情
     */
    @Operation(summary = "获取模型配置详情")
    @GetMapping("/{id}")
    public ResultData getInfo(@PathVariable Long id) {
        return ok(modelConfigService.selectModelConfigById(id));
    }

    /**
     * 新增模型配置
     */
    @Operation(summary = "新增模型配置")
    @Log(title = "模型管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody AiModelConfig config) {
        config.setCreateBy(SecurityUtils.getUsername());
        if (config.getApiKey() != null && config.getApiKey().matches("^\\*+$")) {
            config.setApiKey(null);
        }
        if (config.getSearchKey() != null && config.getSearchKey().matches("^\\*+$")) {
            config.setSearchKey(null);
        }
        // 如果新增配置时指定为默认模型，则先清空同部门（或全局）下该类型（modelType）的其他默认状态
        if ("1".equals(config.getIsDefault()) && config.getModelType() != null) {
            modelConfigService.cleanDefaultStatus(config.getModelType(), config.getDeptId());
        }

        int result = modelConfigService.insertModelConfig(config);
        modelFactory.clearCache(); // 清除工厂缓存以应用最新配置
        return toAjaxResult(result);
    }

    /**
     * 修改模型配置
     */
    @Operation(summary = "修改模型配置")
    @Log(title = "模型管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody AiModelConfig config) {
        AiModelConfig existing = modelConfigService.selectModelConfigById(config.getId());
        if (existing == null) {
            return ResultData.fail("模型配置不存在");
        }

        // 公共模型权限校验：仅超管或创建者可编辑
        if (existing.getDeptId() == null) {
            boolean isAdmin = SecurityUtils.isAdmin();
            String username = SecurityUtils.getUsername();
            if (!isAdmin && !username.equals(existing.getCreateBy())) {
                return ResultData.fail("操作失败，公共模型仅允许超级管理员或原创建者修改");
            }
        }

        config.setUpdateBy(SecurityUtils.getUsername());
        if (config.getApiKey() != null && config.getApiKey().matches("^\\*+$")) {
            config.setApiKey(null);
        }
        if (config.getSearchKey() != null && config.getSearchKey().matches("^\\*+$")) {
            config.setSearchKey(null);
        }
        // 如果更新为默认模型，先清空同部门（或全局）下该类型（modelType）的其他默认状态
        if ("1".equals(config.getIsDefault()) && config.getModelType() != null) {
            modelConfigService.cleanDefaultStatus(config.getModelType(), config.getDeptId());
        }

        int result = modelConfigService.updateModelConfig(config);
        modelFactory.clearCache(); // 清除工厂缓存以应用最新配置
        return toAjaxResult(result);
    }

    /**
     * 删除模型配置
     */
    @Operation(summary = "删除模型配置")
    @Log(title = "模型管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        AiModelConfig existing = modelConfigService.selectModelConfigById(id);
        if (existing == null) {
            return ResultData.fail("模型配置不存在");
        }

        // 公共模型权限校验：仅超管或创建者可删除
        if (existing.getDeptId() == null) {
            boolean isAdmin = SecurityUtils.isAdmin();
            String username = SecurityUtils.getUsername();
            if (!isAdmin && !username.equals(existing.getCreateBy())) {
                return ResultData.fail("操作失败，公共模型仅允许超级管理员或原创建者删除");
            }
        }

        int result = modelConfigService.deleteModelConfigById(id);
        modelFactory.clearCache(); // 清除工厂缓存
        return toAjaxResult(result);
    }

    /**
     * 设为默认聊天模型
     */
    @Operation(summary = "设为默认聊天模型")
    @Log(title = "模型管理", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}/default")
    public ResultData setDefaultChat(@PathVariable Long id) {
        AiModelConfig existing = modelConfigService.selectModelConfigById(id);
        if (existing == null) {
            return ResultData.fail("模型配置不存在");
        }

        // 公共模型权限校验：仅超管或创建者可操作
        if (existing.getDeptId() == null) {
            boolean isAdmin = SecurityUtils.isAdmin();
            String username = SecurityUtils.getUsername();
            if (!isAdmin && !username.equals(existing.getCreateBy())) {
                return ResultData.fail("操作失败，公共模型仅允许超级管理员或原创建者修改");
            }
        }

        modelConfigService.cleanDefaultStatus("CHAT", existing.getDeptId());
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setIsDefault("1");
        int result = modelConfigService.updateModelConfig(config);
        modelFactory.clearCache();
        return toAjaxResult(result);
    }

    /**
     * 设为默认向量模型
     */
    @Operation(summary = "设为默认向量模型")
    @Log(title = "模型管理", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}/defaultEmbedding")
    public ResultData setDefaultEmbedding(@PathVariable Long id) {
        AiModelConfig existing = modelConfigService.selectModelConfigById(id);
        if (existing == null) {
            return ResultData.fail("模型配置不存在");
        }

        // 公共模型权限校验：仅超管或创建者可操作
        if (existing.getDeptId() == null) {
            boolean isAdmin = SecurityUtils.isAdmin();
            String username = SecurityUtils.getUsername();
            if (!isAdmin && !username.equals(existing.getCreateBy())) {
                return ResultData.fail("操作失败，公共模型仅允许超级管理员或原创建者修改");
            }
        }

        modelConfigService.cleanDefaultStatus("EMBEDDING", existing.getDeptId());
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setIsDefault("1");
        int result = modelConfigService.updateModelConfig(config);
        modelFactory.clearCache();
        return toAjaxResult(result);
    }

    /**
     * 设为默认绘图模型
     */
    @Operation(summary = "设为默认绘图模型")
    @Log(title = "模型管理", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}/defaultImage")
    public ResultData setDefaultImage(@PathVariable Long id) {
        AiModelConfig existing = modelConfigService.selectModelConfigById(id);
        if (existing == null) {
            return ResultData.fail("模型配置不存在");
        }

        // 公共模型权限校验
        if (existing.getDeptId() == null) {
            boolean isAdmin = SecurityUtils.isAdmin();
            String username = SecurityUtils.getUsername();
            if (!isAdmin && !username.equals(existing.getCreateBy())) {
                return ResultData.fail("操作失败，公共模型仅允许超级管理员或原创建者修改");
            }
        }

        modelConfigService.cleanDefaultStatus("IMAGE", existing.getDeptId());
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setIsDefault("1");
        int result = modelConfigService.updateModelConfig(config);
        modelFactory.clearCache();
        return toAjaxResult(result);
    }

    /**
     * 拉取远程模型列表
     * 通过后端代理调用各提供商的 OpenAI 兼容 /models 端点，统一返回可用模型 ID 列表
     */
    @Operation(summary = "拉取远程可用模型列表")
    @PostMapping("/list/remote")
    public ResultData<List<String>> fetchModels(@RequestBody FetchModelsRequest req) {
        if (req.getProvider() == null || req.getProvider().trim().isEmpty()) {
            return ResultData.fail("提供商不能为空");
        }
        String provider = req.getProvider().trim().toLowerCase();

        // Ollama 以外的提供商必须提供 API Key
        if (!"ollama".equals(provider) && (req.getApiKey() == null || req.getApiKey().trim().isEmpty())) {
            return ResultData.fail("API Key 不能为空");
        }

        // 非 Ollama 且未提供 Base URL 时，检查是否有已知默认地址
        boolean hasCustomUrl = req.getBaseUrl() != null && !req.getBaseUrl().trim().isEmpty();
        if (!hasCustomUrl && "openai".equals(provider)) {
            return ResultData.fail("当前提供商无默认地址，请填写 API Base URL");
        }

        try {
            String resolvedUrl = resolveBaseUrl(provider, req.getBaseUrl());
            String apiKey = resolveApiKey(provider, req.getApiKey());

            // 智能路径兼容：构建候选 endpoint 列表
            // 用户可能输入 https://api.moonshot.cn（不含 /v1）或 https://api.deepseek.com/v1（已含 /v1）
            List<String> candidateEndpoints = new ArrayList<>();
            String normalizedUrl = resolvedUrl.endsWith("/") ? resolvedUrl.substring(0, resolvedUrl.length() - 1) : resolvedUrl;

            if (normalizedUrl.matches(".*?/v\\d+$")) {
                // URL 已包含版本路径如 /v1, /v2 → 直接追加 /models
                candidateEndpoints.add(normalizedUrl + "/models");
            } else {
                // URL 不含版本路径 → 先试 /v1/models，再试 /models
                candidateEndpoints.add(normalizedUrl + "/v1/models");
                candidateEndpoints.add(normalizedUrl + "/models");
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // 依次尝试候选 endpoint，遇到 200 立即返回
            HttpResponse<String> lastResponse = null;
            for (String endpoint : candidateEndpoints) {
                log.info(">>> 尝试拉取远程模型列表, provider={}, endpoint={}", provider, endpoint);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                lastResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (lastResponse.statusCode() == 401 || lastResponse.statusCode() == 403) {
                    return ResultData.fail("API Key 无效或已过期，请检查后重试");
                }

                if (lastResponse.statusCode() == 200) {
                    List<String> modelIds = parseModelIds(lastResponse.body());
                    if (!modelIds.isEmpty()) {
                        log.info(">>> 成功获取 {} 个模型, provider={}, endpoint={}", modelIds.size(), provider, endpoint);
                        return ok(modelIds);
                    }
                }

                // 404 或空列表 → 继续尝试下一个候选 endpoint
                log.info(">>> endpoint={} 返回 status={}, 尝试下一个候选路径", endpoint, lastResponse.statusCode());
            }

            // 所有候选 endpoint 均失败
            if (lastResponse != null && lastResponse.statusCode() != 200) {
                return ResultData.fail("获取模型列表失败，HTTP 状态码：" + lastResponse.statusCode());
            }
            return ResultData.fail("未获取到可用模型，请检查 API Key 权限或服务地址");

        } catch (java.net.http.HttpTimeoutException e) {
            log.error("拉取模型列表超时, provider={}", provider, e);
            return ResultData.fail("请求超时，请检查网络连接或 API Base URL 是否可达");
        } catch (Exception e) {
            log.error("拉取模型列表异常, provider={}", provider, e);
            return ResultData.fail("获取模型列表失败：" + e.getMessage());
        }
    }

    /**
     * 解析最终 Base URL
     * 核心原则：用户自定义 URL 永远优先（支持中转站/代理），provider 仅用于无 URL 时的默认兜底
     */
    private String resolveBaseUrl(String provider, String customBaseUrl) {
        boolean hasCustomUrl = customBaseUrl != null && !customBaseUrl.trim().isEmpty();

        // ① 用户提供了自定义 URL → 直接使用（支持任意中转站）
        if (hasCustomUrl) {
            String url = customBaseUrl.trim();
            // Ollama 特殊处理：如果 URL 不含 /v1，自动追加以兼容 OpenAI 协议
            if ("ollama".equalsIgnoreCase(provider) && !url.contains("/v1")) {
                return url + "/v1";
            }
            return url;
        }

        // ② 未提供自定义 URL → 使用提供商官方默认地址
        switch (provider) {
            case "deepseek":
                return DEEPSEEK_BASE_URL;
            case "dashscope":
                return DASHSCOPE_BASE_URL;
            case "ollama":
                return OLLAMA_DEFAULT_URL + "/v1";
            case "ark":
                return ARK_BASE_URL;
            default:
                throw new IllegalArgumentException("当前提供商无默认地址，请填写 API Base URL");
        }
    }

    /** Ollama 无需鉴权，使用固定占位 Key */
    private String resolveApiKey(String provider, String apiKey) {
        return "ollama".equals(provider) ? "ollama" : apiKey.trim();
    }

    /**
     * 解析 OpenAI 兼容格式的模型列表 JSON
     * 标准格式: { "data": [{ "id": "model-name", ... }, ...] }
     */
    private List<String> parseModelIds(String responseBody) {
        List<String> result = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            // 标准 OpenAI 格式: { "data": [...] }
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode model : dataNode) {
                    JsonNode idNode = model.get("id");
                    if (idNode != null && !idNode.asText().isEmpty()) {
                        result.add(idNode.asText());
                    }
                }
            }

            // 兜底：Ollama 旧版 /api/tags 格式 { "models": [{ "name": "...", ... }] }
            if (result.isEmpty()) {
                JsonNode modelsNode = root.get("models");
                if (modelsNode != null && modelsNode.isArray()) {
                    for (JsonNode model : modelsNode) {
                        JsonNode nameNode = model.get("name");
                        if (nameNode != null && !nameNode.asText().isEmpty()) {
                            result.add(nameNode.asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析模型列表 JSON 失败", e);
        }
        return result;
    }
}
