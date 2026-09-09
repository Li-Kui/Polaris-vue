package com.polaris.ai.workflow.application;

import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;

import java.util.List;

/** 作废当前审批级别并使用新的审批对象重新发起。 */
public record WorkflowApprovalRestartStageCommand(
        List<WorkflowApprovalTarget> targets,
        String reason,
        Integer expectedLockVersion) {
}
