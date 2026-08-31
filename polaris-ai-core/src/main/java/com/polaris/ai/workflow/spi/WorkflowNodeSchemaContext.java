package com.polaris.ai.workflow.spi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** 动态节点 Schema 解析上下文；资源句柄仅在服务端解析器内部可见。 */
public record WorkflowNodeSchemaContext(
        String nodeId,
        String nodeType,
        String handlerVersion,
        JsonNode config,
        Long tenantId,
        String environment,
        String principalType,
        String principalId,
        Map<String, ResolvedWorkflowResource> resources,
        JsonNode declaredInputSchema,
        JsonNode declaredOutputSchema) {

    public WorkflowNodeSchemaContext {
        resources = resources == null ? Map.of() : Map.copyOf(resources);
    }
}
