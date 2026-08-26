package com.polaris.ai.workflow.application;

import java.util.List;

/** 持久化审批箱和审批决策的应用边界。 */
public interface WorkflowApprovalApplicationFacade {

    List<WorkflowApprovalTaskView> list(String status);

    WorkflowApprovalTaskView decide(
            String approvalTaskId, WorkflowApprovalDecisionCommand command);
}
