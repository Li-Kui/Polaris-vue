package com.polaris.ai.workflow.application;

/** 通过稳定工作流编码启动当前发布版本的开放 API 请求。 */
public record WorkflowExecutionByCodeCommand(
        String workflowCode,
        Object input,
        String environment,
        String idempotencyKey) {
}
