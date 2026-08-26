package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

/** 通过稳定工作流编码启动当前发布版本的开放 API 请求。 */
public record WorkflowExecutionByCodeCommand(
        String workflowCode,
        JsonNode input,
        String environment,
        String idempotencyKey) {
}
