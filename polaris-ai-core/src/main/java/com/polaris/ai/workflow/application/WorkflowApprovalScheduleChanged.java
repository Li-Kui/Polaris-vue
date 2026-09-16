package com.polaris.ai.workflow.application;

/** 审批提醒或截止时间发生变化后，请求重建最近的 Quartz 闹钟。 */
public record WorkflowApprovalScheduleChanged() {

    public static final WorkflowApprovalScheduleChanged REFRESH =
            new WorkflowApprovalScheduleChanged();
}
