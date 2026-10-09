package com.polaris.platform.controller;

import com.polaris.ai.workflow.application.WorkflowShareDefaultsCommand;
import com.polaris.ai.workflow.application.WorkflowShareDefinitionView;
import com.polaris.common.core.domain.ResultData;
import com.polaris.platform.domain.WorkflowShare;
import com.polaris.platform.service.IWorkflowShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 中台管理端：工作流分享接口
 *
 * @author polaris
 */
@Tag(name = "中台管理端：工作流分享")
@RestController
@RequestMapping("/platform/workflow-shares")
public class PlatformConsoleShareController {

    private final IWorkflowShareService shareService;

    public PlatformConsoleShareController(IWorkflowShareService shareService) {
        this.shareService = shareService;
    }

    @Operation(summary = "查询租户的分享列表")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping
    public ResultData<List<WorkflowShare>> list(
            @RequestParam(value = "workflowDefinitionId", required = false) Long workflowDefinitionId) {
        if (workflowDefinitionId != null) {
            return ResultData.ok(shareService.selectShareListByWorkflowDefinitionId(workflowDefinitionId));
        }
        return ResultData.ok(shareService.selectShareListByTenantId(null));
    }

    @Operation(summary = "获取工作流默认分享配置")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/definition/{definitionId}")
    public ResultData<WorkflowShareDefinitionView> definition(@PathVariable Long definitionId) {
        return ResultData.ok(shareService.getShareDefinition(definitionId));
    }

    @Operation(summary = "保存工作流默认分享配置")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PutMapping("/definition/{definitionId}")
    public ResultData<WorkflowShareDefinitionView> updateDefinition(
            @PathVariable Long definitionId, @RequestBody WorkflowShareDefaultsCommand command) {
        return ResultData.ok(shareService.updateShareDefaults(definitionId, command));
    }

    @Operation(summary = "创建工作流分享")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping
    public ResultData<WorkflowShare> create(@RequestBody WorkflowShare share) {
        return ResultData.ok(shareService.createShare(share));
    }

    @Operation(summary = "更新工作流分享")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PutMapping
    public ResultData<Integer> update(@RequestBody WorkflowShare share) {
        return ResultData.ok(shareService.updateShare(share));
    }

    @Operation(summary = "删除工作流分享")
    @PreAuthorize("@workflowAccess.canEdit()")
    @DeleteMapping("/{id}")
    public ResultData<Integer> delete(@PathVariable("id") Long id) {
        return ResultData.ok(shareService.deleteShare(id));
    }
}
