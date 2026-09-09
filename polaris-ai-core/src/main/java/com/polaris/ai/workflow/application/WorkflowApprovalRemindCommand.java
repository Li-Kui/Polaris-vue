package com.polaris.ai.workflow.application;

/** 对当前审批级别发送一次受审计的催办事件。 */
public record WorkflowApprovalRemindCommand(
        String message,
        String requestId,
        Integer expectedLockVersion) {
}
