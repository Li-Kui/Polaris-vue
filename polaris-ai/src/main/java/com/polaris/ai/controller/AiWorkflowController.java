package com.polaris.ai.controller;

import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.workflow.langgraph.LangGraph4jEngine;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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
    private LangGraph4jEngine langGraph4jEngine;

    @Autowired(required = false)
    @Qualifier("workflowTaskExecutor")
    private TaskExecutor taskExecutor;

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
        // 工作流配置变更时清除拓扑缓存
        if (workflow.getGraphJson() != null) {
            langGraph4jEngine.invalidateTopologyCache(workflow.getWorkflowCode());
        }
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
     * 流式执行指定智能体工作流（LangGraph4j 图引擎）
     * GET /ai/workflow/stream?workflowCode=xxx&message=yyy&threadId=zzz
     *
     * @param workflowCode 工作流编码
     * @param message      用户输入的提示词
     * @param threadId     线程ID（可选，用于 Checkpoint 追踪）
     * @return SseEmitter 实例
     */
    @Operation(summary = "流式执行智能体工作流")
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter runWorkflowStream(
            @RequestParam String workflowCode,
            @RequestParam String message,
            @RequestParam(required = false) String threadId,
            @RequestParam(required = false) Long conversationId) {

        Long userId = SecurityUtils.getUserId();
        SseEmitter emitter = new SseEmitter(0L);
        final SecurityContext securityContext = SecurityContextHolder.getContext();

        Runnable task = () -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                langGraph4jEngine.run(workflowCode, message, threadId, securityContext, emitter, conversationId, userId);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        // 优先使用 Spring TaskExecutor，降级使用裸线程
        if (taskExecutor != null) {
            taskExecutor.execute(task);
        } else {
            new Thread(task).start();
        }

        return emitter;
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * 获取工作流的 Mermaid 拓扑图
     * GET /ai/workflow/graph?workflowCode=xxx
     *
     * @param workflowCode 工作流编码
     * @return Mermaid 格式的图定义字符串
     */
    @Operation(summary = "获取工作流拓扑可视化")
    @GetMapping("/graph")
    public ResultData<String> getGraph(@RequestParam String workflowCode) {
        String mermaid = langGraph4jEngine.generateMermaid(workflowCode);
        return ok(mermaid);
    }

    /**
     * 审批流恢复流式执行接口
     * POST /ai/workflow/resume
     *
     * @param workflowCode 工作流编码
     * @param threadId     会话线程ID
     * @param resumeInput  包含审批操作等数据（如 approve: true/false, feedback: "xxx"）
     * @return SseEmitter 实例
     */
    @Operation(summary = "恢复被挂起的工作流执行")
    @PostMapping(value = "/resume", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter resumeWorkflow(
            @RequestParam String workflowCode,
            @RequestParam String threadId,
            @RequestParam(required = false) Long conversationId,
            @RequestBody java.util.Map<String, Object> resumeInput) {

        Long userId = SecurityUtils.getUserId();
        SseEmitter emitter = new SseEmitter(0L);

        // 并发乐观锁校验：只有当 status = 'paused' (挂起) 时，才可以通过更新状态成功获取锁
        int updated = jdbcTemplate.update(
                "UPDATE ai_graph_checkpoint SET status = 'running', update_time = NOW() " +
                "WHERE thread_id = ? AND status = 'paused'", threadId);

        if (updated <= 0) {
            try {
                emitter.send(SseEmitter.event().name("error").data("当前会话已在处理或不处于挂起审批状态"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        final SecurityContext securityContext = SecurityContextHolder.getContext();

        Runnable task = () -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                langGraph4jEngine.resume(workflowCode, threadId, resumeInput, securityContext, emitter, conversationId, userId);
            } finally {
                SecurityContextHolder.clearContext();
            }
        };

        if (taskExecutor != null) {
            taskExecutor.execute(task);
        } else {
            new Thread(task).start();
        }

        return emitter;
    }
}

