package com.polaris.ai.controller;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.modelcenter.service.ModelAggregateAccessGuard;
import com.polaris.ai.service.IAiModelConfigService;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 旧业务页面使用的模型只读查询接口。
 *
 * <p>模型的新增、修改、删除、测试与默认设置统一由
 * {@code /ai/model-center} Aggregate API 提供，避免继续写入旧固定字段。</p>
 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "AI模型查询")
@RestController
@RequestMapping("/ai/model")
public class AiModelConfigController extends BaseController {

    private final IAiModelConfigService modelConfigService;
    private final ModelAggregateAccessGuard accessGuard;

    public AiModelConfigController(
            IAiModelConfigService modelConfigService,
            ModelAggregateAccessGuard accessGuard) {
        this.modelConfigService = modelConfigService;
        this.accessGuard = accessGuard;
    }

    @Operation(summary = "查询模型概要列表")
    @GetMapping("/list")
    public ResultData<Page<AiModelConfig>> list(AiModelConfig config) {
        startPage();
        return ok(getDataPage(
                modelConfigService.selectModelConfigList(config)));
    }

    @Operation(summary = "查询当前用户可用的聊天模型")
    @GetMapping("/list/available")
    public ResultData<List<AiModelConfig>> listAvailable(
            CallerContext caller) {
        return ok(modelConfigService.selectAvailableModelConfigsByCapability(
                "CHAT_COMPLETION", caller.getDeptId(),
                caller.isSuperAdmin()));
    }

    @Operation(summary = "查询当前用户可用的向量模型")
    @GetMapping("/list/availableEmbedding")
    public ResultData<List<AiModelConfig>> listAvailableEmbedding(
            CallerContext caller) {
        return ok(modelConfigService.selectAvailableModelConfigsByCapability(
                "TEXT_EMBEDDING", caller.getDeptId(),
                caller.isSuperAdmin()));
    }

    @Operation(summary = "获取模型稳定概要")
    @GetMapping("/{id}")
    public ResultData<AiModelConfig> getInfo(@PathVariable Long id) {
        return ok(accessGuard.requireAccessible(id));
    }
}
