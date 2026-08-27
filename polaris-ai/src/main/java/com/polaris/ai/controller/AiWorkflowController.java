package com.polaris.ai.controller;

import com.polaris.ai.workflow.application.*;
import com.polaris.ai.workflow.definition.WorkflowCompilationResult;
import com.polaris.ai.workflow.runtime.WorkflowEventStreamService;
import com.polaris.ai.workflow.spi.WorkflowNodeDescriptor;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.controller.BaseController;
import com.polaris.common.core.domain.ResultData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/** 工作流草稿、校验、发布和执行的共享接口。 */
@Tag(name = "AI工作流")
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@RestController
@RequestMapping("/ai/workflow")
public class AiWorkflowController extends BaseController {

    private final WorkflowDefinitionApplicationFacade workflowFacade;
    private final WorkflowApprovalApplicationFacade approvalFacade;
    private final WorkflowExecutionApplicationFacade executionFacade;
    private final WorkflowResourceBindingApplicationFacade resourceBindingFacade;
    private final WorkflowResourceCatalogApplicationFacade resourceCatalogFacade;
    private final WorkflowEventStreamService eventStreamService;
    private final WorkflowTriggerApplicationFacade triggerFacade;
    private final WorkflowArtifactApplicationFacade artifactFacade;

    public AiWorkflowController(
            WorkflowDefinitionApplicationFacade workflowFacade,
            WorkflowApprovalApplicationFacade approvalFacade,
            WorkflowExecutionApplicationFacade executionFacade,
            WorkflowResourceBindingApplicationFacade resourceBindingFacade,
            WorkflowResourceCatalogApplicationFacade resourceCatalogFacade,
            WorkflowEventStreamService eventStreamService,
            WorkflowTriggerApplicationFacade triggerFacade,
            WorkflowArtifactApplicationFacade artifactFacade) {
        this.workflowFacade = workflowFacade;
        this.approvalFacade = approvalFacade;
        this.executionFacade = executionFacade;
        this.resourceBindingFacade = resourceBindingFacade;
        this.resourceCatalogFacade = resourceCatalogFacade;
        this.eventStreamService = eventStreamService;
        this.triggerFacade = triggerFacade;
        this.artifactFacade = artifactFacade;
    }

    @Operation(summary = "查询工作流定义")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/definitions")
    public ResultData<List<WorkflowDefinitionView>> listDefinitions() {
        return ok(workflowFacade.listDefinitions());
    }

    @Operation(summary = "创建工作流草稿")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping("/definitions")
    public ResultData<WorkflowDefinitionView> createDraft(
            @RequestBody WorkflowDraftCommand command) {
        return ok(workflowFacade.createDraft(command));
    }

    @Operation(summary = "查询工作流定义详情")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/definitions/{definitionId}")
    public ResultData<WorkflowDefinitionView> getDefinition(@PathVariable Long definitionId) {
        return ok(workflowFacade.getDefinition(definitionId));
    }

    @Operation(summary = "保存工作流草稿")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PutMapping("/definitions/{definitionId}/draft")
    public ResultData<WorkflowDefinitionView> updateDraft(
            @PathVariable Long definitionId,
            @RequestBody WorkflowDraftCommand command) {
        return ok(workflowFacade.updateDraft(definitionId, command));
    }

    @Operation(summary = "校验并编译工作流草稿")
    @PreAuthorize("@workflowAccess.canEdit() or @workflowAccess.canDebug()")
    @PostMapping("/definitions/{definitionId}/validate")
    public ResultData<WorkflowCompilationResult> validateDraft(@PathVariable Long definitionId) {
        return ok(workflowFacade.validateDraft(definitionId));
    }

    @Operation(summary = "发布不可变工作流版本")
    @PreAuthorize("@workflowAccess.canPublish()")
    @PostMapping("/definitions/{definitionId}/publish")
    public ResultData<WorkflowPublishResult> publish(
            @PathVariable Long definitionId,
            @RequestBody WorkflowPublishCommand command) {
        return ok(workflowFacade.publish(definitionId, command));
    }

    @Operation(summary = "查询工作流发布版本")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/definitions/{definitionId}/versions")
    public ResultData<List<WorkflowPublishedVersionView>> listVersions(
            @PathVariable Long definitionId) {
        return ok(workflowFacade.listVersions(definitionId));
    }

    @Operation(summary = "查询工作流发布版本详情")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/definitions/{definitionId}/versions/{versionId}")
    public ResultData<WorkflowPublishedVersionDetailView> getVersion(
            @PathVariable Long definitionId,
            @PathVariable String versionId) {
        return ok(workflowFacade.getVersion(definitionId, versionId));
    }

    @Operation(summary = "将发布版本恢复为工作流草稿")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping("/definitions/{definitionId}/versions/{versionId}/rollback")
    public ResultData<WorkflowDefinitionView> rollbackDraft(
            @PathVariable Long definitionId,
            @PathVariable String versionId,
            @RequestBody WorkflowRollbackCommand command) {
        return ok(workflowFacade.rollbackDraft(definitionId, versionId, command));
    }

    @Operation(summary = "克隆工作流定义")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping("/definitions/{definitionId}/clone")
    public ResultData<WorkflowDefinitionView> cloneDefinition(
            @PathVariable Long definitionId,
            @RequestBody WorkflowCloneCommand command) {
        return ok(workflowFacade.cloneDefinition(definitionId, command));
    }

    @Operation(summary = "查询工作流可用节点描述")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/node-descriptors")
    public ResultData<Collection<WorkflowNodeDescriptor>> listNodeDescriptors() {
        return ok(workflowFacade.listNodeDescriptors());
    }

    @Operation(summary = "启动工作流持久化执行")
    @PreAuthorize("@workflowAccess.canExecute()")
    @PostMapping("/executions")
    public ResultData<WorkflowExecutionView> startExecution(
            @RequestBody WorkflowExecutionStartCommand command) {
        return ok(executionFacade.start(command));
    }

    @Operation(summary = "按工作流编码启动持久化执行")
    @PreAuthorize("@workflowAccess.canExecute()")
    @PostMapping("/executions/by-code")
    public ResultData<WorkflowExecutionView> startExecutionByCode(
            @RequestBody WorkflowExecutionByCodeCommand command) {
        return ok(executionFacade.startByCode(command));
    }

    @Operation(summary = "查询工作流执行列表")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/executions")
    public ResultData<List<WorkflowExecutionView>> listExecutions(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String status) {
        return ok(executionFacade.list(definitionId, status));
    }

    @Operation(summary = "查询工作流执行详情")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/executions/{executionId}")
    public ResultData<WorkflowExecutionView> getExecution(@PathVariable String executionId) {
        return ok(executionFacade.get(executionId));
    }

    @Operation(summary = "查询工作流节点运行记录")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/executions/{executionId}/node-runs")
    public ResultData<List<WorkflowNodeRunView>> listNodeRuns(
            @PathVariable String executionId) {
        return ok(executionFacade.listNodeRuns(executionId));
    }

    @Operation(summary = "查询工作流执行产物")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/executions/{executionId}/artifacts")
    public ResultData<List<WorkflowArtifactView>> listArtifacts(
            @PathVariable String executionId) {
        return ok(artifactFacade.list(executionId));
    }

    @Operation(summary = "下载工作流执行产物")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/executions/{executionId}/artifacts/{artifactId}/content")
    public void downloadArtifact(
            @PathVariable String executionId,
            @PathVariable String artifactId,
            HttpServletResponse response) throws java.io.IOException {
        WorkflowArtifactContent artifact = artifactFacade.load(executionId, artifactId);
        byte[] content = artifact.content();
        response.setContentType(artifact.mimeType());
        response.setContentLength(content.length);
        String encodedName = URLEncoder.encode(
                artifact.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + encodedName);
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

    @Operation(summary = "重放工作流执行事件")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/executions/{executionId}/events")
    public ResultData<List<WorkflowExecutionEventView>> listEvents(
            @PathVariable String executionId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "200") int limit) {
        return ok(executionFacade.listEvents(executionId, afterSequence, limit));
    }

    @Operation(summary = "SSE观察工作流持久化事件")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping(value = "/executions/{executionId}/events/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable String executionId,
            @RequestParam(defaultValue = "0") long afterSequence) {
        return eventStreamService.subscribe(executionId, afterSequence);
    }

    @Operation(summary = "取消工作流执行")
    @PreAuthorize("@workflowAccess.canCancel()")
    @PostMapping("/executions/{executionId}/cancel")
    public ResultData<WorkflowExecutionView> cancelExecution(
            @PathVariable String executionId) {
        return ok(executionFacade.cancel(executionId));
    }

    @Operation(summary = "安全重试工作流执行")
    @PreAuthorize("@workflowAccess.canExecute()")
    @PostMapping("/executions/{executionId}/retry")
    public ResultData<WorkflowExecutionView> retryExecution(
            @PathVariable String executionId,
            @RequestBody(required = false) WorkflowExecutionRetryCommand command) {
        return ok(executionFacade.retry(executionId, command));
    }

    @Operation(summary = "查询工作流审批箱")
    @PreAuthorize("@workflowAccess.canApprove()")
    @GetMapping("/approvals")
    public ResultData<List<WorkflowApprovalTaskView>> listApprovals(
            @RequestParam(required = false) String status) {
        return ok(approvalFacade.list(status));
    }

    @Operation(summary = "处理工作流审批任务")
    @PreAuthorize("@workflowAccess.canApprove()")
    @PostMapping("/approvals/{approvalTaskId}/decision")
    public ResultData<WorkflowApprovalTaskView> decideApproval(
            @PathVariable String approvalTaskId,
            @RequestBody WorkflowApprovalDecisionCommand command) {
        return ok(approvalFacade.decide(approvalTaskId, command));
    }

    @Operation(summary = "查询工作流资源绑定")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/resource-bindings")
    public ResultData<List<WorkflowResourceBindingView>> listResourceBindings(
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String environment) {
        return ok(resourceBindingFacade.list(definitionId, environment));
    }

    @Operation(summary = "查询工作流可用的现有资源")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/resources")
    public ResultData<List<WorkflowResourceOption>> listResources(
            @RequestParam String kind,
            @RequestParam(required = false) String environment) {
        return ok(resourceCatalogFacade.list(kind, environment));
    }

    @Operation(summary = "保存工作流资源绑定")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping("/resource-bindings")
    public ResultData<WorkflowResourceBindingView> saveResourceBinding(
            @RequestBody WorkflowResourceBindingCommand command) {
        return ok(resourceBindingFacade.save(command));
    }

    @Operation(summary = "停用工作流资源绑定")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping("/resource-bindings/{bindingId}/disable")
    public ResultData<WorkflowResourceBindingView> disableResourceBinding(
            @PathVariable Long bindingId) {
        return ok(resourceBindingFacade.disable(bindingId));
    }

    @Operation(summary = "查询工作流触发器")
    @PreAuthorize("@workflowAccess.canRead()")
    @GetMapping("/triggers")
    public ResultData<List<WorkflowTriggerView>> listTriggers(
            @RequestParam(required = false) Long definitionId) {
        return ok(triggerFacade.list(definitionId));
    }

    @Operation(summary = "创建工作流触发器")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PostMapping("/triggers")
    public ResultData<WorkflowTriggerView> createTrigger(
            @RequestBody WorkflowTriggerCommand command) {
        return ok(triggerFacade.create(command));
    }

    @Operation(summary = "启停工作流触发器")
    @PreAuthorize("@workflowAccess.canEdit()")
    @PutMapping("/triggers/{triggerId}/status")
    public ResultData<WorkflowTriggerView> updateTriggerStatus(
            @PathVariable String triggerId,
            @RequestBody WorkflowTriggerStatusCommand command) {
        return ok(triggerFacade.updateStatus(triggerId, command));
    }

    @Operation(summary = "调用工作流触发器")
    @PreAuthorize("@workflowAccess.canExecute()")
    @PostMapping("/triggers/{triggerId}/invoke")
    public ResultData<WorkflowExecutionView> invokeTrigger(
            @PathVariable String triggerId,
            @RequestBody(required = false) WorkflowTriggerInvocationCommand command) {
        return ok(triggerFacade.invoke(triggerId, command));
    }
}
