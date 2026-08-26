package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

/** 创建绑定到一个不可变发布版本的触发器。 */
public record WorkflowTriggerCommand(
        Long definitionId,
        String workflowVersionId,
        String triggerType,
        JsonNode config,
        JsonNode dedupPolicy) {
}
