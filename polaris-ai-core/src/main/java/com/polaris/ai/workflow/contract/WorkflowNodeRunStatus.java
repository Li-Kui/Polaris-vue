package com.polaris.ai.workflow.contract;

/** 单个逻辑节点运行的持久化状态。 */
public enum WorkflowNodeRunStatus {
    PENDING,
    READY,
    RUNNING,
    WAITING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    CANCELLED,
    NEEDS_ATTENTION;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELLED;
    }
}
