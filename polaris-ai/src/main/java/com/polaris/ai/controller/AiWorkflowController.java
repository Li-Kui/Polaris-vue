package com.polaris.ai.controller;

import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.workflow.WorkflowEngine;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI智能体工作流控制器
 * 提供工作流配置获取、CRUD 管理与 SSE 流式执行端点
 *
 * @author polaris
 */
@Slf4j
@Tag(name = "AI智能体工作流")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/workflow")
public class AiWorkflowController extends BaseController {

    @Autowired
    private IAiWorkflowService workflowService;

    @Autowired
    private WorkflowEngine workflowEngine;

    /**
     * 条件分页查询工作流配置列表（管理后台使用）
     * GET /ai/workflow/list
     */
    @Operation(summary = "条件分页查询工作流列表")
    @GetMapping("/list")
    public ResultData<Page<AiWorkflow>> list(AiWorkflow workflow) {
        startPage();
        List<AiWorkflow> list = workflowService.selectWorkflowList(workflow);
        return ok(getDataPage(list));
    }

    /**
     * 获取所有启用的工作流列表（前端聊天页面选用下拉框使用）
     * GET /ai/workflow/list/active
     */
    @Operation(summary = "获取所有启用的工作流列表")
    @GetMapping("/list/active")
    public ResultData<List<AiWorkflow>> listActive() {
        List<AiWorkflow> list = workflowService.listActiveWorkflows();
        return ok(list);
    }

    /**
     * 获取工作流详细信息
     * GET /ai/workflow/{id}
     */
    @Operation(summary = "获取工作流详情")
    @GetMapping("/{id}")
    public ResultData getInfo(@PathVariable Long id) {
        return ok(workflowService.getById(id));
    }

    /**
     * 新增工作流配置
     * POST /ai/workflow
     */
    @Operation(summary = "新增工作流配置")
    @Log(title = "工作流管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData add(@RequestBody AiWorkflow workflow) {
        workflow.setCreateBy(SecurityUtils.getUsername());
        return toAjaxResult(workflowService.save(workflow));
    }

    /**
     * 修改工作流配置
     * PUT /ai/workflow
     */
    @Operation(summary = "修改工作流配置")
    @Log(title = "工作流管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData edit(@RequestBody AiWorkflow workflow) {
        workflow.setUpdateBy(SecurityUtils.getUsername());
        return toAjaxResult(workflowService.updateById(workflow));
    }

    /**
     * 逻辑删除工作流配置
     * DELETE /ai/workflow/{id}
     */
    @Operation(summary = "删除工作流配置")
    @Log(title = "工作流管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData remove(@PathVariable Long id) {
        return toAjaxResult(workflowService.removeById(id));
    }

    /**
     * 流式执行指定智能体工作流
     * GET /ai/workflow/stream?workflowCode=xxx&message=yyy
     *
     * @param workflowCode 工作流编码
     * @param message      用户输入的提示词
     * @return SseEmitter 实例
     */
    @Operation(summary = "流式执行智能体工作流")
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter runWorkflowStream(@RequestParam String workflowCode, @RequestParam String message) {
        SseEmitter emitter = new SseEmitter(0L);
        final SecurityContext securityContext = SecurityContextHolder.getContext();

        new Thread(() -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                workflowEngine.run(workflowCode, message, securityContext, emitter);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }).start();

        return emitter;
    }
}
