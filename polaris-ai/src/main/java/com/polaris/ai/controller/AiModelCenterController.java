package com.polaris.ai.controller;

import com.polaris.ai.modelcenter.dto.ModelAggregateSaveRequest;
import com.polaris.ai.modelcenter.modeltest.CapabilityTestResult;
import com.polaris.ai.modelcenter.modeltest.ModelTestDraftRequest;
import com.polaris.ai.modelcenter.modeltest.ModelTestDraftService;
import com.polaris.ai.modelcenter.schema.options.SchemaOption;
import com.polaris.ai.modelcenter.schema.options.SchemaOptionsRequest;
import com.polaris.ai.modelcenter.schema.options.SchemaOptionsService;
import com.polaris.ai.modelcenter.service.ModelAggregateService;
import com.polaris.ai.modelcenter.service.ModelDefaultInternalService;
import com.polaris.ai.modelcenter.vo.ModelAggregateVO;
import com.polaris.ai.modelcenter.vo.ModelEditorContextVO;
import com.polaris.ai.modelcenter.vo.ModelSummaryVO;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Schema Driven Model Editor 与默认模型的 V2 API。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "AI Model Center V2")
@RestController
@RequestMapping("/ai/model-center")
public class AiModelCenterController {

    private final ModelAggregateService aggregateService;
    private final ModelDefaultInternalService defaultService;
    private final ModelTestDraftService modelTestService;
    private final SchemaOptionsService optionsService;

    public AiModelCenterController(
            ModelAggregateService aggregateService,
            ModelDefaultInternalService defaultService,
            ModelTestDraftService modelTestService,
            SchemaOptionsService optionsService) {
        this.aggregateService = aggregateService;
        this.defaultService = defaultService;
        this.modelTestService = modelTestService;
        this.optionsService = optionsService;
    }

    @Operation(summary = "获取 Model Editor 上下文")
    @GetMapping("/editor-context")
    public ResultData<ModelEditorContextVO> editorContext() {
        return ResultData.ok(aggregateService.editorContext());
    }

    @Operation(summary = "获取 Model Aggregate")
    @GetMapping("/models/{id}")
    public ResultData<ModelAggregateVO> get(@PathVariable Long id) {
        return ResultData.ok(aggregateService.get(id));
    }

    @Operation(summary = "查询可访问模型列表")
    @GetMapping("/models")
    public ResultData<List<ModelSummaryVO>> list() {
        return ResultData.ok(aggregateService.list());
    }

    @Operation(summary = "新增 Model Aggregate")
    @Log(title = "Model Center", businessType = BusinessType.INSERT)
    @PostMapping("/models")
    public ResultData<Long> create(
            @RequestBody ModelAggregateSaveRequest request) {
        return ResultData.ok(aggregateService.create(request));
    }

    @Operation(summary = "修改 Model Aggregate")
    @Log(title = "Model Center", businessType = BusinessType.UPDATE)
    @PutMapping("/models/{id}")
    public ResultData<Void> update(
            @PathVariable Long id,
            @RequestBody ModelAggregateSaveRequest request) {
        aggregateService.update(id, request);
        return ResultData.ok();
    }

    @Operation(summary = "删除 Model Aggregate")
    @Log(title = "Model Center", businessType = BusinessType.DELETE)
    @DeleteMapping("/models/{id}")
    public ResultData<Void> delete(@PathVariable Long id) {
        aggregateService.delete(id);
        return ResultData.ok();
    }

    @Operation(summary = "设置当前作用域默认模型")
    @PutMapping("/defaults/{capabilityCode}/{modelId}")
    public ResultData<Void> setDefault(
            @PathVariable String capabilityCode,
            @PathVariable Long modelId) {
        defaultService.setForCurrentScope(capabilityCode, modelId);
        return ResultData.ok();
    }

    @Operation(summary = "读取当前作用域默认模型")
    @GetMapping("/defaults/{capabilityCode}")
    public ResultData<Long> getDefault(
            @PathVariable String capabilityCode) {
        return ResultData.ok(defaultService.resolveModelId(capabilityCode));
    }

    @Operation(summary = "测试未保存的模型草稿")
    @PostMapping("/model-test")
    public ResultData<CapabilityTestResult> testDraft(
            @RequestBody ModelTestDraftRequest request) {
        return ResultData.ok(modelTestService.test(request));
    }

    @Operation(summary = "解析 Schema 动态选项")
    @PostMapping("/options")
    public ResultData<List<SchemaOption>> options(
            @RequestBody SchemaOptionsRequest request) {
        return ResultData.ok(optionsService.resolve(request));
    }
}
