package com.polaris.ai.workflow.application;

/** 一次受幂等保护的人工审批决定。 */
public record WorkflowApprovalDecisionCommand(
        String decision,
        String comment,
        Integer expectedLockVersion) {
}
