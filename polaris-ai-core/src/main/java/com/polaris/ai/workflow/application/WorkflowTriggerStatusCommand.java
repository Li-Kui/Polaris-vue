package com.polaris.ai.workflow.application;

/** 工作流触发器的乐观状态更新命令。 */
public record WorkflowTriggerStatusCommand(String status, Integer expectedLockVersion) {
}
