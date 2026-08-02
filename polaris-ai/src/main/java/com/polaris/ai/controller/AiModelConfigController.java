package com.polaris.ai.controller;

import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.dto.FetchModelsRequest;
import com.polaris.ai.pivot.AiModelFactory;
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
