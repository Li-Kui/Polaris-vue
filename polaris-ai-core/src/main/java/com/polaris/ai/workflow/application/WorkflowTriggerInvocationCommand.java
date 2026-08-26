package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

/** 已认证的触发器调用参数。 */
public record WorkflowTriggerInvocationCommand(JsonNode input, String idempotencyKey) {
}
