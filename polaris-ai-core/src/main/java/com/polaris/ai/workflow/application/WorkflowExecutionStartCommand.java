package com.polaris.ai.workflow.application;

/** 基于不可变发布版本启动一次执行的请求。 */
public record WorkflowExecutionStartCommand(
        Long definitionId,
        String workflowVersionId,
        Object input,
        String environment,
        String idempotencyKey,
        Object approvalSimulation) {
}
