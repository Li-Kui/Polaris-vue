package com.polaris.ai.workflow.application;

/** 事务提交后同步单个工作流定时触发器的轻量信号。 */
public record WorkflowTriggerScheduleChanged(String triggerId) {
}
