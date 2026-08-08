package com.polaris.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.dto.FetchModelsRequest;
import com.polaris.ai.pivot.AiModelFactory;
import com.polaris.ai.service.IAiKnowledgeService;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.ai.service.IAiRemoteModelService;
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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

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

    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private AiModelFactory modelFactory;

    @Autowired
    private IAiRemoteModelService remoteModelService;

    @Autowired
    private IAiKnowledgeService knowledgeService;

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
     * 查询当前用户可用于知识库的向量模型。
     */
    @Operation(summary = "查询当前用户可用的向量模型列表")
    @GetMapping("/list/availableEmbedding")
    public ResultData<List<AiModelConfig>> listAvailableEmbedding() {
        Long userId = SecurityUtils.getUserId();
        boolean isAdmin = SecurityUtils.isAdmin(userId);
        Long deptId = null;
        if (!isAdmin && SecurityUtils.getLoginUser() != null
                && SecurityUtils.getLoginUser().getUser() != null) {
            deptId = SecurityUtils.getLoginUser().getUser().getDeptId();
        }
        return ok(modelConfigService.selectAvailableModelConfigsByType("EMBEDDING", deptId, isAdmin));
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
        String validationError = validateEmbeddingConfig(config);
        if (validationError != null) {
            return ResultData.fail(validationError);
        }
        if ("EMBEDDING".equalsIgnoreCase(config.getModelType())
                && "1".equals(config.getIsDefault())) {
            return ResultData.fail("请先保存向量模型，再通过“设为默认向量”执行连接与维度探测");
        }
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
        mergeEmbeddingFieldsForValidation(config, existing);
        String validationError = validateEmbeddingConfig(config);
        if (validationError != null) {
            return ResultData.fail(validationError);
        }
        boolean embeddingSpaceChanged = changesEmbeddingSpace(existing, config);
        if ("EMBEDDING".equalsIgnoreCase(config.getModelType())
                && "1".equals(config.getIsDefault())
                && !"1".equals(existing.getIsDefault())) {
            return ResultData.fail("请通过“设为默认向量”执行连接与维度探测");
        }
        if (embeddingSpaceChanged && "1".equals(existing.getIsDefault())) {
            config.setIsDefault("0");
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
        if (result > 0 && embeddingSpaceChanged) {
            knowledgeService.markIndexesStaleByEmbeddingModelId(
                    existing.getId(), "绑定的向量模型配置已变更，请重建知识库索引");
        }
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
        if ("EMBEDDING".equalsIgnoreCase(existing.getModelType())) {
            long referencedKnowledgeBases = knowledgeService.count(
                    new LambdaQueryWrapper<AiKnowledgeBase>()
                            .eq(AiKnowledgeBase::getEmbeddingModelId, id));
            if (referencedKnowledgeBases > 0) {
                return ResultData.fail("该向量模型仍被 " + referencedKnowledgeBases + " 个知识库绑定，不能删除");
            }
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
        if (!"CHAT".equalsIgnoreCase(existing.getModelType())) {
            return ResultData.fail("只有 CHAT 类型模型可以设为默认聊天模型");
        }
        if (!"1".equals(existing.getStatus())) {
            return ResultData.fail("聊天模型未启用，不能设为默认模型");
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
        if (!"EMBEDDING".equalsIgnoreCase(existing.getModelType())) {
            return ResultData.fail("只有 EMBEDDING 类型模型可以设为默认向量模型");
        }
        if (!"1".equals(existing.getStatus())) {
            return ResultData.fail("向量模型未启用，不能设为默认模型");
        }

        // 公共模型权限校验：仅超管或创建者可操作
        if (existing.getDeptId() == null) {
            boolean isAdmin = SecurityUtils.isAdmin();
            String username = SecurityUtils.getUsername();
            if (!isAdmin && !username.equals(existing.getCreateBy())) {
                return ResultData.fail("操作失败，公共模型仅允许超级管理员或原创建者修改");
            }
        }

        try {
            modelFactory.probeEmbeddingDimension(existing);
        } catch (Exception e) {
            log.warn("向量模型启用探测失败, id={}", id, e);
            return ResultData.fail("向量模型连接或维度探测失败：" + e.getMessage());
        }

        modelConfigService.cleanDefaultStatus("EMBEDDING", existing.getDeptId());
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setIsDefault("1");
        int result = modelConfigService.updateModelConfig(config);
        modelFactory.clearCache();
        return toAjaxResult(result);
    }

    private String validateEmbeddingConfig(AiModelConfig config) {
        if (config == null || !"EMBEDDING".equalsIgnoreCase(config.getModelType())) {
            return null;
        }
        if (config.getEmbeddingDimension() != null && config.getEmbeddingDimension() <= 0) {
            return "向量维度必须大于 0";
        }
        String dimensionMode = config.getEmbeddingDimensionMode();
        if (dimensionMode == null || dimensionMode.trim().isEmpty()) {
            config.setEmbeddingDimensionMode("MODEL_DEFAULT");
        } else {
            dimensionMode = dimensionMode.trim().toUpperCase(Locale.ROOT);
            if (!"MODEL_DEFAULT".equals(dimensionMode) && !"REQUEST".equals(dimensionMode)) {
                return "向量维度模式只支持 MODEL_DEFAULT 或 REQUEST";
            }
            config.setEmbeddingDimensionMode(dimensionMode);
        }
        if ("REQUEST".equals(config.getEmbeddingDimensionMode())
                && config.getEmbeddingDimension() == null) {
            return "REQUEST 维度模式必须填写向量维度";
        }
        if (config.getEmbeddingBatchSize() == null) {
            config.setEmbeddingBatchSize(16);
        } else if (config.getEmbeddingBatchSize() < 1 || config.getEmbeddingBatchSize() > 2048) {
            return "向量化批量大小必须处于 1-2048";
        }
        if (config.getEmbeddingMaxInputTokens() != null && config.getEmbeddingMaxInputTokens() <= 0) {
            return "向量模型最大输入 Token 数必须大于 0";
        }
        return null;
    }

    private void mergeEmbeddingFieldsForValidation(AiModelConfig update, AiModelConfig existing) {
        if (update.getModelType() == null) update.setModelType(existing.getModelType());
        if (update.getEmbeddingDimension() == null) {
            update.setEmbeddingDimension(existing.getEmbeddingDimension());
        }
        if (update.getEmbeddingDimensionMode() == null) {
            update.setEmbeddingDimensionMode(existing.getEmbeddingDimensionMode());
        }
        if (update.getEmbeddingMaxInputTokens() == null) {
            update.setEmbeddingMaxInputTokens(existing.getEmbeddingMaxInputTokens());
        }
        if (update.getEmbeddingBatchSize() == null) {
            update.setEmbeddingBatchSize(existing.getEmbeddingBatchSize());
        }
        if (update.getStatus() == null) update.setStatus(existing.getStatus());
    }

    private boolean changesEmbeddingSpace(AiModelConfig existing, AiModelConfig update) {
        if (!"EMBEDDING".equalsIgnoreCase(existing.getModelType())
                && !"EMBEDDING".equalsIgnoreCase(update.getModelType())) {
            return false;
        }
        return changed(existing.getProvider(), update.getProvider())
                || changed(existing.getModelName(), update.getModelName())
                || changed(existing.getBaseUrl(), update.getBaseUrl())
                || changed(existing.getAccessMode(), update.getAccessMode())
                || changed(existing.getModelType(), update.getModelType())
                || changed(existing.getEmbeddingDimension(), update.getEmbeddingDimension())
                || changed(existing.getEmbeddingDimensionMode(), update.getEmbeddingDimensionMode())
                || changed(existing.getStatus(), update.getStatus());
    }

    private boolean changed(Object current, Object update) {
        return update != null && !Objects.equals(current, update);
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
        if (!"IMAGE".equalsIgnoreCase(existing.getModelType())) {
            return ResultData.fail("只有 IMAGE 类型模型可以设为默认绘图模型");
        }
        if (!"1".equals(existing.getStatus())) {
            return ResultData.fail("绘图模型未启用，不能设为默认模型");
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
     * 拉取远程可用模型列表
     */
    @Operation(summary = "拉取远程可用模型列表")
    @PostMapping("/list/remote")
    public ResultData<List<String>> fetchModels(@RequestBody FetchModelsRequest req) {
        // 参数校验
        if (req.getProvider() == null || req.getProvider().trim().isEmpty()) {
            return ResultData.fail("提供商不能为空");
        }
        String provider = req.getProvider().trim().toLowerCase();
        if (!"ollama".equals(provider) && (req.getApiKey() == null || req.getApiKey().trim().isEmpty())) {
            return ResultData.fail("API Key 不能为空");
        }
        if ("openai".equals(provider) && (req.getBaseUrl() == null || req.getBaseUrl().trim().isEmpty())) {
            return ResultData.fail("当前提供商无默认地址，请填写 API Base URL");
        }
        // 中转站模式下必须填写 baseUrl
        if ("relay".equalsIgnoreCase(req.getAccessMode())
                && (req.getBaseUrl() == null || req.getBaseUrl().trim().isEmpty())) {
            return ResultData.fail("中转站模式下必须填写 API Base URL");
        }

        // 委托 Service 层执行
        try {
            List<String> modelIds = remoteModelService.fetchRemoteModels(req);
            return ok(modelIds);
        } catch (IllegalArgumentException e) {
            return ResultData.fail(e.getMessage());
        } catch (Exception e) {
            log.error("拉取远程模型列表失败, provider={}", provider, e);
            return ResultData.fail(e.getMessage());
        }
    }
}
