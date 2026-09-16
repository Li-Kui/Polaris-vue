package com.polaris.ai.workflow.application;

/** 事务发件箱新增记录后的轻量唤醒信号。 */
public record WorkflowOutboxSignal() {

    public static final WorkflowOutboxSignal PENDING = new WorkflowOutboxSignal();
}
