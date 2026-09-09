package com.polaris.ai.workflow.spi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/** 动态解析后的节点输入输出契约和可展示诊断。 */
public record ResolvedNodeSchema(
        JsonNode inputSchema,
        JsonNode outputSchema,
        String source,
        String sourceVersion,
        Map<String, String> fieldSources,
        List<String> diagnostics) {

    public ResolvedNodeSchema {
        source = source == null || source.isBlank() ? "NODE_CONTRACT" : source;
        fieldSources = fieldSources == null ? Map.of() : Map.copyOf(fieldSources);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
