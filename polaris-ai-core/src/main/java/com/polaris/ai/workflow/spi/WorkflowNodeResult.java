package com.polaris.ai.workflow.spi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** 节点成功输出及不含密钥的用量数据。 */
public record WorkflowNodeResult(
        JsonNode output,
        Map<String, Number> usage,
        String sideEffectStatus) {

    public WorkflowNodeResult {
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }

    public static WorkflowNodeResult success(JsonNode output) {
        return new WorkflowNodeResult(output, Map.of(), "NONE");
    }
}
