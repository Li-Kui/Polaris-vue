package com.polaris.ai.workflow.application;

/** 轻量唤醒信号，不携带业务数据，只触发消费者去数据库取数。 */
public record WorkflowTaskSignal(String reason) {
    public static final WorkflowTaskSignal EXECUTION_QUEUED =
            new WorkflowTaskSignal("EXECUTION_QUEUED");
    public static final WorkflowTaskSignal APPROVAL_COMPLETED =
            new WorkflowTaskSignal("APPROVAL_COMPLETED");
}
