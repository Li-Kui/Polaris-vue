package com.polaris.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.utils.ChatContextHolder;
import com.polaris.ai.workflow.api.WorkflowApprovalRequest;
import com.polaris.ai.workflow.api.WorkflowRunRequest;
import com.polaris.ai.workflow.event.WorkflowSsePublisher;
import com.polaris.ai.workflow.langgraph.GraphTopology;
import com.polaris.ai.workflow.langgraph.LangGraph4jEngine;
import com.polaris.ai.workflow.runtime.WorkflowCancellationRegistry;
import com.polaris.ai.workflow.runtime.WorkflowExecutionService;
import com.polaris.ai.workflow.runtime.WorkflowExecutionStore;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.annotation.Log;
import com.polaris.common.annotation.RateLimiter;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.common.enums.BusinessType;
import com.polaris.common.enums.LimitType;
import com.polaris.common.exception.ServiceException;
import com.polaris.common.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** AI 工作流 V2.1 管理和执行接口。 */
@Slf4j
@Tag(name = "AI智能体工作流")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/workflow")
public class AiWorkflowController extends BaseController {

    private final IAiWorkflowService workflowService;
    private final LangGraph4jEngine langGraph4jEngine;
    private final AsyncTaskExecutor taskExecutor;
    private final WorkflowExecutionService executionService;
    private final WorkflowExecutionStore executionStore;
    private final WorkflowCancellationRegistry cancellationRegistry;
    private final WorkflowSsePublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AiWorkflowController(
            IAiWorkflowService workflowService,
            LangGraph4jEngine langGraph4jEngine,
            @Qualifier("workflowTaskExecutor") AsyncTaskExecutor taskExecutor,
            WorkflowExecutionService executionService,
            WorkflowExecutionStore executionStore,
            WorkflowCancellationRegistry cancellationRegistry,
            WorkflowSsePublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.langGraph4jEngine = langGraph4jEngine;
        this.taskExecutor = taskExecutor;
        this.executionService = executionService;
        this.executionStore = executionStore;
        this.cancellationRegistry = cancellationRegistry;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "条件分页查询工作流列表")
    @PreAuthorize("@ss.hasPermi('ai:workflow:list')")
    @GetMapping("/list")
    public ResultData<Page<AiWorkflow>> list(AiWorkflow workflow) {
        startPage();
        return ok(getDataPage(workflowService.selectWorkflowList(workflow)));
    }

    @Operation(summary = "获取所有启用的工作流列表")
    @PreAuthorize("hasRole('PLATFORM_USER') or @ss.hasPermi('ai:workflow:execute')")
    @GetMapping("/list/active")
    public ResultData<List<AiWorkflow>> listActive() {
        return ok(workflowService.listActiveWorkflows());
    }

    @Operation(summary = "获取已注册的 Java 工作流节点")
    @PreAuthorize("@ss.hasPermi('ai:workflow:query') or @ss.hasPermi('ai:workflow:add') " +
            "or @ss.hasPermi('ai:workflow:edit')")
    @GetMapping("/executors/available")
    public ResultData<List<Map<String, String>>> listExecutors() {
        return ok(langGraph4jEngine.listJavaExecutors());
    }

    @Operation(summary = "获取工作流详情")
    @PreAuthorize("@ss.hasPermi('ai:workflow:query')")
    @GetMapping("/{id}")
    public ResultData<AiWorkflow> getInfo(@PathVariable Long id) {
        return ok(workflowService.getById(id));
    }

    @Operation(summary = "新增工作流配置")
    @PreAuthorize("@ss.hasPermi('ai:workflow:add')")
    @Log(title = "工作流管理", businessType = BusinessType.INSERT)
    @PostMapping
    public ResultData<Void> add(@Validated @RequestBody AiWorkflow workflow) {
        workflow.setCreateBy(SecurityUtils.getUsername());
        return toAjaxResult(workflowService.save(workflow));
    }

    @Operation(summary = "修改工作流配置")
    @PreAuthorize("@ss.hasPermi('ai:workflow:edit')")
    @Log(title = "工作流管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public ResultData<Void> edit(@Validated @RequestBody AiWorkflow workflow) {
        workflow.setUpdateBy(SecurityUtils.getUsername());
        return toAjaxResult(workflowService.updateById(workflow));
    }

    @Operation(summary = "删除工作流配置")
    @PreAuthorize("@ss.hasPermi('ai:workflow:remove')")
    @Log(title = "工作流管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public ResultData<Void> remove(@PathVariable Long id) {
        return toAjaxResult(workflowService.removeById(id));
    }

    @Operation(summary = "流式执行智能体工作流")
    @PreAuthorize("(!#request.testRun and (hasRole('PLATFORM_USER') or @ss.hasPermi('ai:workflow:execute'))) " +
            "or (#request.testRun and @ss.hasPermi('ai:workflow:test'))")
    @RateLimiter(time = 60, count = 20, limitType = LimitType.IP)
    @PostMapping(value = "/executions/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter runWorkflowStream(@Validated @RequestBody WorkflowRunRequest request) {
        Long userId = SecurityUtils.getUserId();
        WorkflowExecutionStore.Execution execution = executionService.prepare(
                request.getWorkflowCode(), request.getMessage(), request.getFileUrl(),
                request.getConversationId(), userId, request.isTestRun());
        SseEmitter emitter = new SseEmitter(600_000L);
        AtomicBoolean cancelled = cancellationRegistry.register(execution.executionId());
        configureEmitter(emitter, execution.executionId(), cancelled);
        SecurityContext securityContext = copySecurityContext();

        Runnable task = () -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                ChatContextHolder.setConversationId(execution.conversationId());
                ChatContextHolder.setFileUrl(execution.fileUrl());
                langGraph4jEngine.run(execution, securityContext, emitter, cancelled);
            } finally {
                ChatContextHolder.clear();
                SecurityContextHolder.clearContext();
                cancellationRegistry.unregister(execution.executionId());
            }
        };
        submit(execution.executionId(), emitter, task);
        return emitter;
    }

    @Operation(summary = "获取工作流拓扑可视化")
    @PreAuthorize("@ss.hasPermi('ai:workflow:query')")
    @GetMapping("/graph")
    public ResultData<String> getGraph(@RequestParam String workflowCode) {
        return ok(langGraph4jEngine.generateMermaid(workflowCode));
    }

    @Operation(summary = "预览工作流实时 Mermaid 拓扑图")
    @PreAuthorize("@ss.hasPermi('ai:workflow:add') or @ss.hasPermi('ai:workflow:edit')")
    @PostMapping("/preview-mermaid")
    public ResultData<String> previewMermaid(@RequestBody Map<String, String> body) {
        String graphJson = body.get("graphJson");
        if (graphJson == null || graphJson.isBlank()) {
            return ok("graph TD\n  START((Start)) --> END((End))");
        }
        try {
            GraphTopology topology = objectMapper.readValue(graphJson, GraphTopology.class);
            topology.validate();
            return ok(langGraph4jEngine.buildMermaid(topology));
        } catch (IllegalArgumentException e) {
            return ok("graph TD\n  ERROR[\"配置异常: "
                    + e.getMessage().replace("\"", "\\\"") + "\"]");
        } catch (Exception e) {
            log.error("实时生成预览 Mermaid 失败", e);
            return ok("graph TD\n  ERROR[\"拓扑格式损坏，无法渲染图表\"]");
        }
    }

    @Operation(summary = "查询当前用户的待审批工作流")
    @PreAuthorize("hasRole('PLATFORM_USER') or @ss.hasPermi('ai:workflow:approve')")
    @GetMapping("/approvals/pending")
    public ResultData<List<Map<String, Object>>> listPendingApprovals() {
        return ok(executionStore.listPendingApprovals(SecurityUtils.getUserId()));
    }

    /** 拒绝分支只写终态，绝不调用受保护节点。 */
    @Operation(summary = "审批工作流并恢复执行")
    @PreAuthorize("hasRole('PLATFORM_USER') or @ss.hasPermi('ai:workflow:approve')")
    @PostMapping(value = "/executions/{executionId}/approvals/{approvalId}/decision",
            produces = "text/event-stream;charset=UTF-8")
    public SseEmitter decideApproval(
            @PathVariable String executionId,
            @PathVariable String approvalId,
            @Validated @RequestBody WorkflowApprovalRequest request) {
        return decideApproval(executionId, approvalId, request, false);
    }

    @Operation(summary = "审批试运行工作流并恢复执行")
    @PreAuthorize("@ss.hasPermi('ai:workflow:test')")
    @PostMapping(value = "/executions/{executionId}/test-approvals/{approvalId}/decision",
            produces = "text/event-stream;charset=UTF-8")
    public SseEmitter decideTestApproval(
            @PathVariable String executionId,
            @PathVariable String approvalId,
            @Validated @RequestBody WorkflowApprovalRequest request) {
        return decideApproval(executionId, approvalId, request, true);
    }

    private SseEmitter decideApproval(
            String executionId, String approvalId, WorkflowApprovalRequest request,
            boolean expectedTestRun) {
        Long userId = SecurityUtils.getUserId();
        WorkflowExecutionStore.Execution execution = executionService.getOwned(executionId, userId);
        if (execution.testRun() != expectedTestRun) {
            throw new ServiceException(expectedTestRun
                    ? "该执行不是试运行，不能使用试运行审批接口"
                    : "试运行审批必须在工作流编辑器中处理");
        }
        SseEmitter emitter = new SseEmitter(600_000L);

        if (!Boolean.TRUE.equals(request.getApprove())) {
            boolean rejected = executionStore.reject(
                    executionId, approvalId, execution.userId(), userId, request.getFeedback());
            sendDecisionResult(emitter, executionId, rejected, "workflow_rejected", approvalId,
                    request.getFeedback());
            return emitter;
        }

        AtomicBoolean cancelled;
        try {
            // 先预留运行槽，再消费一次性审批，避免清理竞态把执行卡在 RUNNING。
            cancelled = cancellationRegistry.register(executionId);
        } catch (IllegalStateException e) {
            sendBusyResult(emitter, executionId);
            return emitter;
        }

        boolean approved;
        try {
            approved = executionStore.approve(
                    executionId, approvalId, execution.userId(), userId, request.getFeedback());
        } catch (RuntimeException e) {
            cancellationRegistry.unregister(executionId);
            throw e;
        }
        if (!approved) {
            cancellationRegistry.unregister(executionId);
            sendDecisionResult(emitter, executionId, false, "error", approvalId, null);
            return emitter;
        }

        configureEmitter(emitter, executionId, cancelled);
        SecurityContext securityContext = copySecurityContext();
        Runnable task = () -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                ChatContextHolder.setConversationId(execution.conversationId());
                ChatContextHolder.setFileUrl(execution.fileUrl());
                langGraph4jEngine.resume(
                        execution, request.getFeedback(), securityContext, emitter, cancelled);
            } finally {
                ChatContextHolder.clear();
                SecurityContextHolder.clearContext();
                cancellationRegistry.unregister(executionId);
            }
        };
        submit(executionId, emitter, task);
        return emitter;
    }

    @Operation(summary = "取消工作流执行")
    @PreAuthorize("hasRole('PLATFORM_USER') or @ss.hasPermi('ai:workflow:execute') " +
            "or @ss.hasPermi('ai:workflow:test')")
    @PostMapping("/executions/{executionId}/cancel")
    public ResultData<Void> cancelExecution(@PathVariable String executionId) {
        WorkflowExecutionStore.Execution execution =
                executionService.getOwned(executionId, SecurityUtils.getUserId());
        cancellationRegistry.cancel(execution.executionId());
        executionStore.markCancelled(execution.executionId());
        return ok();
    }

    private void sendDecisionResult(SseEmitter emitter, String executionId, boolean success,
                                    String event, String approvalId, String feedback) {
        if (success) {
            eventPublisher.send(emitter, executionId, event, null,
                    Map.of("approvalId", approvalId,
                            "feedback", feedback == null ? "" : feedback));
        } else {
            eventPublisher.send(emitter, executionId, "error", null,
                    Map.of("message", "审批任务已处理或执行状态已变化"));
        }
        emitter.complete();
        eventPublisher.release(executionId);
    }

    /** 上一运行任务仍在收尾时，不释放其事件序列，也不消费审批。 */
    private void sendBusyResult(SseEmitter emitter, String executionId) {
        eventPublisher.send(emitter, executionId, "error", null,
                Map.of("message", "工作流上一阶段仍在收尾，请稍后重试审批"));
        emitter.complete();
    }

    private void configureEmitter(SseEmitter emitter, String executionId, AtomicBoolean cancelled) {
        emitter.onTimeout(() -> {
            cancelled.set(true);
            cancellationRegistry.cancel(executionId);
            executionStore.markCancelled(executionId);
            emitter.complete();
        });
        emitter.onError(error -> {
            cancelled.set(true);
            cancellationRegistry.cancel(executionId);
            executionStore.markCancelled(executionId);
        });
    }

    private SecurityContext copySecurityContext() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        return context;
    }

    private void submit(String executionId, SseEmitter emitter, Runnable task) {
        try {
            cancellationRegistry.attachFuture(executionId, taskExecutor.submitCompletable(task));
        } catch (TaskRejectedException e) {
            cancellationRegistry.unregister(executionId);
            executionStore.markFailed(executionId, "工作流执行队列已满");
            try {
                eventPublisher.send(emitter, executionId, "error", null,
                        Map.of("message", "工作流执行队列已满，请稍后重试"));
            } catch (Exception ignored) {
            } finally {
                emitter.complete();
                eventPublisher.release(executionId);
            }
        }
    }
}
