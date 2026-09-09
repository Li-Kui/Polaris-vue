package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** 对前端安全的节点有效 Schema，不包含资源句柄和凭据。 */
public record WorkflowResolvedNodeSchemaView(
        String nodeId,
        String nodeType,
        String handlerVersion,
        JsonNode inputSchema,
        JsonNode outputSchema,
        String source,
        String sourceVersion,
        Map<String, String> fieldSources,
        List<String> diagnostics) {
}
