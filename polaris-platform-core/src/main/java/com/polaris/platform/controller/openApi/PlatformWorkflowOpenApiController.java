package com.polaris.platform.controller.openApi;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.workflow.application.WorkflowExecutionApplicationFacade;
import com.polaris.ai.workflow.application.WorkflowExecutionByCodeCommand;
import com.polaris.ai.workflow.application.WorkflowExecutionEventView;
import com.polaris.ai.workflow.application.WorkflowExecutionView;
import com.polaris.ai.workflow.contract.WorkflowPermission;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.platform.dto.PlatformWorkflowExecutionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 由同一持久化工作流应用门面提供支持的 API 密钥接口。 */
@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台工作流开放API")
@RestController
@RequestMapping("/platform/api")
public class PlatformWorkflowOpenApiController {

    private final WorkflowExecutionApplicationFacade workflowFacade;

    public PlatformWorkflowOpenApiController(WorkflowExecutionApplicationFacade workflowFacade) {
        this.workflowFacade = workflowFacade;
    }

    @Operation(summary = "按编码启动已发布工作流")
    @PostMapping("/workflows/{workflowCode}/executions")
    public ResultData<WorkflowExecutionView> start(
            @PathVariable String workflowCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PlatformWorkflowExecutionRequest request) {
        requirePermission(WorkflowPermission.EXECUTE);
        PlatformWorkflowExecutionRequest value = request == null
                ? new PlatformWorkflowExecutionRequest(null, null) : request;
        return ResultData.ok(workflowFacade.startByCode(new WorkflowExecutionByCodeCommand(
                workflowCode, value.input(), value.environment(), idempotencyKey)));
    }

    @Operation(summary = "查询工作流执行")
    @GetMapping("/workflow-executions/{executionId}")
    public ResultData<WorkflowExecutionView> get(@PathVariable String executionId) {
        requirePermission(WorkflowPermission.READ);
        return ResultData.ok(workflowFacade.get(executionId));
    }

    @Operation(summary = "从持久化序号续读工作流事件")
    @GetMapping("/workflow-executions/{executionId}/events")
    public ResultData<List<WorkflowExecutionEventView>> events(
            @PathVariable String executionId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(required = false) Long afterSequence,
            @RequestParam(defaultValue = "200") int limit) {
        requirePermission(WorkflowPermission.READ);
        long after = afterSequence == null ? parseSequence(lastEventId) : afterSequence;
        return ResultData.ok(workflowFacade.listEvents(executionId, after, limit));
    }

    @Operation(summary = "取消工作流执行")
    @PostMapping("/workflow-executions/{executionId}/cancel")
    public ResultData<WorkflowExecutionView> cancel(@PathVariable String executionId) {
        requirePermission(WorkflowPermission.CANCEL);
        return ResultData.ok(workflowFacade.cancel(executionId));
    }

    private void requirePermission(String permission) {
        CallerContext context = CallerContextHolder.require();
        if (!context.hasPermission(permission)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "API Key缺少权限: " + permission);
        }
    }

    private long parseSequence(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Last-Event-ID格式无效");
        }
    }
}
