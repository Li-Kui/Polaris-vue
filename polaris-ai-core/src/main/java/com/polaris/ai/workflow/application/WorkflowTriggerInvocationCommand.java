package com.polaris.ai.workflow.application;

/** 已认证的触发器调用参数。 */
public record WorkflowTriggerInvocationCommand(Object input, String idempotencyKey) {
}
