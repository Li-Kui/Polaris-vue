package com.polaris.ai.workflow.application;

import com.polaris.ai.workflow.spi.WorkflowApprovalTarget;

import java.util.List;

/** 管理员为配置异常的审批级别补充有效审批人并恢复执行。 */
public record WorkflowApprovalRepairCommand(
        List<WorkflowApprovalTarget> targets,
        String reason,
        Integer expectedLockVersion) {
}
