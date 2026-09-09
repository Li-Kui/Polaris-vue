package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** 从单节点试运行的脱敏输出推导出的编辑器提示 Schema。 */
public record WorkflowInferredSchemaResult(
        String testRunId,
        Long definitionId,
        Long draftRevision,
        String nodeId,
        JsonNode schema,
        String inferredAt,
        int sampleCount,
        String nodeConfigHash,
        String schemaSourceVersion,
        List<String> diagnostics) {

    public WorkflowInferredSchemaResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
