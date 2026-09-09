package com.polaris.ai.workflow.application;

/** 创建或更新可修改工作流草稿的命令。 */
public record WorkflowDraftCommand(String definitionJson, Long expectedRevision) {
}
