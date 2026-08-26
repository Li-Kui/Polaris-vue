package com.polaris.ai.workflow.application;

/** 绑定编辑器当前草稿修订号的乐观发布命令。 */
public record WorkflowPublishCommand(Long expectedRevision) {
}
