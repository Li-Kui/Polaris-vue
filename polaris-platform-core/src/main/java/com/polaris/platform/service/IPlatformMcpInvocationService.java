package com.polaris.platform.service;

import com.polaris.ai.workflow.spi.WorkflowNodeContext;
import com.polaris.common.core.page.Page;
import com.polaris.platform.domain.PlatformMcpInvocation;
import com.polaris.platform.dto.McpInvokeRequest;
import com.polaris.platform.dto.McpInvokeResult;

public interface IPlatformMcpInvocationService {
    McpInvokeResult invoke(Long toolId, McpInvokeRequest request);
    McpInvokeResult invokeWorkflow(Long toolId, McpInvokeRequest request, WorkflowNodeContext context);
    Page<PlatformMcpInvocation> selectList(Long connectionId, Long toolId, String status, Integer pageNum, Integer pageSize, String executionId, String nodeRunId);
}
