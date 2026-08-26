package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

/** 基于不可变发布版本启动一次执行的请求。 */
public record WorkflowExecutionStartCommand(
        Long definitionId,
        String workflowVersionId,
        JsonNode input,
        String environment,
        String idempotencyKey) {
}
