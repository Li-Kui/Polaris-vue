package com.polaris.ai.workflow.application;

/** 创建绑定到一个不可变发布版本的触发器。 */
public record WorkflowTriggerCommand(
        Long definitionId,
        String workflowVersionId,
        String triggerType,
        Object config,
        Object dedupPolicy) {
}
