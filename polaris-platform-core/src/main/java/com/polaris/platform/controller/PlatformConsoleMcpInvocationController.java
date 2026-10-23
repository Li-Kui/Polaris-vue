package com.polaris.platform.controller;

import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;
import com.polaris.common.core.domain.ResultData;
import com.polaris.common.core.page.Page;
import com.polaris.platform.domain.PlatformMcpInvocation;
import com.polaris.platform.dto.McpInvokeRequest;
import com.polaris.platform.dto.McpInvokeResult;
import com.polaris.platform.service.IPlatformMcpInvocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@ApiGroup(ApiVersionConstants.VERSION_2_0_0)
@Tag(name = "中台MCP受控调用")
@RestController
@RequestMapping("/platform/console/mcp")
public class PlatformConsoleMcpInvocationController {
    private final IPlatformMcpInvocationService service;
    public PlatformConsoleMcpInvocationController(IPlatformMcpInvocationService service) { this.service = service; }

    @Operation(summary = "管理员显式调用当前版本已审批只读工具，不自动重试")
    @PostMapping("/tools/{toolId}/invoke")
    public ResultData<McpInvokeResult> invoke(@PathVariable Long toolId, @RequestBody McpInvokeRequest request) {
        return ResultData.ok(service.invoke(toolId, request));
    }

    @GetMapping("/invocations/list")
    public ResultData<Page<PlatformMcpInvocation>> list(Long connectionId, Long toolId, String status, Integer pageNum, Integer pageSize) {
        return ResultData.ok(service.selectList(connectionId, toolId, status, pageNum, pageSize, null, null));
    }

    @Operation(summary = "按工作流执行查询本租户MCP调用元数据，仅管理员可用")
    @GetMapping("/invocations/workflow/{executionId}")
    public ResultData<Page<PlatformMcpInvocation>> workflowRecords(@PathVariable String executionId, String status, Integer pageNum, Integer pageSize) {
        return ResultData.ok(service.selectList(null, null, status, pageNum, pageSize, executionId, null));
    }

    @GetMapping("/invocations/workflow/{executionId}/nodes/{nodeRunId}")
    public ResultData<Page<PlatformMcpInvocation>> nodeRecords(@PathVariable String executionId, @PathVariable String nodeRunId,
                                                            String status, Integer pageNum, Integer pageSize) {
        return ResultData.ok(service.selectList(null, null, status, pageNum, pageSize, executionId, nodeRunId));
    }

    /** 避免通用异常处理器把JSON解析异常中的原始调用参数写入日志。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResultData<Void> unreadable(HttpMessageNotReadableException ignored) {
        return ResultData.fail("MCP调用请求格式无效，请使用JSON对象与正确的快照字段；未发起远端调用");
    }
}
