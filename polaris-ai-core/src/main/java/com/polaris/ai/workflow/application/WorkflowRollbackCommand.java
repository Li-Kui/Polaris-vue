package com.polaris.ai.workflow.application;

/** 将不可变发布版本恢复为可修改草稿的命令。 */
public record WorkflowRollbackCommand(Long expectedRevision) {
}
