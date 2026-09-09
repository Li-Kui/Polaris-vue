package com.polaris.ai.workflow.application;

import com.polaris.ai.workflow.spi.WorkflowApprovalDirectoryEntry;

import java.util.List;

/** 持久化审批箱和审批决策的应用边界。 */
public interface WorkflowApprovalApplicationFacade {

    List<WorkflowApprovalTaskView> list(String status);

    WorkflowApprovalTaskView get(String approvalInstanceId);

    List<WorkflowApprovalDirectoryEntry> listDirectory(String keyword);

    WorkflowApprovalTaskView decide(
            String approvalInstanceId, WorkflowApprovalDecisionCommand command);

    WorkflowApprovalTaskView repair(
            String approvalInstanceId, WorkflowApprovalRepairCommand command);

    WorkflowApprovalTaskView reassign(
            String approvalInstanceId, WorkflowApprovalReassignCommand command);

    WorkflowApprovalTaskView restartStage(
            String approvalInstanceId, WorkflowApprovalRestartStageCommand command);

    WorkflowApprovalTaskView remind(
            String approvalInstanceId, WorkflowApprovalRemindCommand command);
}
