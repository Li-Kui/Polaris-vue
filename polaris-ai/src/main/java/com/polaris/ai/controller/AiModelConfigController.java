package com.polaris.ai.controller;

import com.polaris.ai.domain.AiModelConfig;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置控制器
 * 
 * @author polaris
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "AI模型管理")
@RestController
@RequestMapping("/ai/model")
public class AiModelConfigController extends BaseController
{
    @Autowired
    private IAiModelConfigService modelConfigService;

    @Autowired
    private AiModelFactory modelFactory;

    /**
     * 查询模型配置列表
     */
    @Operation(summary = "查询模型配置列表")
    @GetMapping("/list")
    public ResultData<Page<AiModelConfig>> list(AiModelConfig config)
    {
        startPage();
        List<AiModelConfig> list = modelConfigService.selectModelConfigList(config);
        return ok(getDataPage(list));
    }

    /**
     * 查询当前登录用户可用的模型列表（系统共享 + 指定部门独享）
     */
    @Operation(summary = "查询当前用户可用的大模型列表")
    @GetMapping("/list/available")
    public ResultData<List<AiModelConfig>> listAvailable()
    {
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
    public ResultData getInfo(@PathVariable Long id)
    {
        return ok(modelConfigService.selectModelConfigById(id));
    }

    /**
     * 新增模型配置
     */
    @Operation(summary = "新增模型配置")
    @Log(title = "模型管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody AiModelConfig config)
    {
        config.setCreateBy(SecurityUtils.getUsername());
        if (config.getApiKey() != null && config.getApiKey().matches("^\\*+$")) {
            config.setApiKey(null);
        }
        if (config.getSearchKey() != null && config.getSearchKey().matches("^\\*+$")) {
            config.setSearchKey(null);
        }
        // 如果新增配置时指定为默认，则先清空同部门（或全局）的其他默认状态
        if ("1".equals(config.getIsDefault())) {
            modelConfigService.cleanDefaultChatStatus(config.getDeptId());
        }
        if ("1".equals(config.getIsDefaultEmbedding())) {
            modelConfigService.cleanDefaultEmbeddingStatus(config.getDeptId());
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
    public ResultData edit(@RequestBody AiModelConfig config)
    {
        config.setUpdateBy(SecurityUtils.getUsername());
        if (config.getApiKey() != null && config.getApiKey().matches("^\\*+$")) {
            config.setApiKey(null);
        }
        if (config.getSearchKey() != null && config.getSearchKey().matches("^\\*+$")) {
            config.setSearchKey(null);
        }
        // 如果更新为默认，先清空同部门（或全局）的其他默认状态
        if ("1".equals(config.getIsDefault())) {
            modelConfigService.cleanDefaultChatStatus(config.getDeptId());
        }
        if ("1".equals(config.getIsDefaultEmbedding())) {
            modelConfigService.cleanDefaultEmbeddingStatus(config.getDeptId());
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
    public ResultData remove(@PathVariable Long id)
    {
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
    public ResultData setDefaultChat(@PathVariable Long id)
    {
        AiModelConfig existing = modelConfigService.selectModelConfigById(id);
        if (existing == null) {
            return ResultData.fail("模型配置不存在");
        }
        modelConfigService.cleanDefaultChatStatus(existing.getDeptId());
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
    public ResultData setDefaultEmbedding(@PathVariable Long id)
    {
        AiModelConfig existing = modelConfigService.selectModelConfigById(id);
        if (existing == null) {
            return ResultData.fail("模型配置不存在");
        }
        modelConfigService.cleanDefaultEmbeddingStatus(existing.getDeptId());
        AiModelConfig config = new AiModelConfig();
        config.setId(id);
        config.setIsDefaultEmbedding("1");
        int result = modelConfigService.updateModelConfig(config);
        modelFactory.clearCache();
        return toAjaxResult(result);
    }
}
