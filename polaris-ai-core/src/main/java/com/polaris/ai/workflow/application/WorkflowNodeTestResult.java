package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** 单节点隔离试运行结果；输入输出均已脱敏。 */
public record WorkflowNodeTestResult(
        String testRunId,
        Long definitionId,
        Long draftRevision,
        String nodeId,
        String nodeType,
        String handlerVersion,
        String environment,
        String mode,
        String status,
        String sideEffect,
        JsonNode input,
        JsonNode output,
        Map<String, Number> usage,
        String schemaSource,
        String schemaSourceVersion,
        List<String> schemaDiagnostics,
        String errorCode,
        String errorMessage,
        long durationMs) {

    public WorkflowNodeTestResult {
        usage = usage == null ? Map.of() : Map.copyOf(usage);
        schemaDiagnostics = schemaDiagnostics == null
                ? List.of() : List.copyOf(schemaDiagnostics);
    }
}
