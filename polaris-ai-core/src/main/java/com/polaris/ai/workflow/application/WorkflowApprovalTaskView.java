package com.polaris.ai.workflow.application;

import java.util.Date;

/** 管理端和租户控制台共用的不含密钥审批任务视图。 */
public record WorkflowApprovalTaskView(
        String approvalInstanceId,
        String executionId,
        String nodeRunId,
        String approvalMode,
        Integer requiredApprovals,
        Boolean allowSelfApproval,
        String status,
        String decisionSummary,
        Date deadline,
        Integer lockVersion,
        Date createTime,
        Date finishTime,
        String contentSnapshot,
        String currentStageName,
        Integer currentStageSequence,
        Integer totalStageCount) {
}
