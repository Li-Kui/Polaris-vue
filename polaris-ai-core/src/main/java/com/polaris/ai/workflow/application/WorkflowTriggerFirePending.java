package com.polaris.ai.workflow.application;

/** 事务提交后唤醒持久化触发批次派发器的轻量信号。 */
public record WorkflowTriggerFirePending(String triggerId) {
}
