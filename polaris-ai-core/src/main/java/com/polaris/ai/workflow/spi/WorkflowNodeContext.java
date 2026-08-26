package com.polaris.ai.workflow.spi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** 传递给一次节点处理尝试且不含密钥的执行上下文。 */
public record WorkflowNodeContext(
        String executionId,
        String nodeRunId,
        int attemptNo,
        Long tenantId,
        String principalType,
        String principalId,
        JsonNode input,
        JsonNode config,
        Map<String, ResolvedWorkflowResource> resources,
        WorkflowCancellation cancellation) {

    public WorkflowNodeContext {
        resources = resources == null ? Map.of() : Map.copyOf(resources);
        cancellation = cancellation == null ? WorkflowCancellation.NONE : cancellation;
    }
}
