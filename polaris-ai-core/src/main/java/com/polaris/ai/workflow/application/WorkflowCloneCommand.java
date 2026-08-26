package com.polaris.ai.workflow.application;

/** 从现有工作流定义创建新草稿的命令。 */
public record WorkflowCloneCommand(String workflowCode, String workflowName) {
}
