package com.polaris.ai.workflow.application;

/** 安全重试完整执行时可选的调用方幂等键。 */
public record WorkflowExecutionRetryCommand(String idempotencyKey) {
}
