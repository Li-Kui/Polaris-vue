package com.polaris.ai.workflow.application;

import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;

import java.util.List;

/** 管理员重新指派当前审批级别尚未处理的审批资格。 */
public record WorkflowApprovalReassignCommand(
        List<WorkflowApprovalTarget> targets,
        String reason,
        Integer expectedLockVersion) {
}
