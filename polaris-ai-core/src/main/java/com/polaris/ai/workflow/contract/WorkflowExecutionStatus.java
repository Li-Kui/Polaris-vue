package com.polaris.ai.workflow.contract;

import java.util.EnumSet;
import java.util.Set;

/** 持久化执行状态及其允许的状态机迁移。 */
public enum WorkflowExecutionStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    WAITING_TIMER,
    WAITING_EVENT,
    RECOVERING,
    NEEDS_ATTENTION,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REJECTED;

    public boolean canTransitionTo(WorkflowExecutionStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == REJECTED;
    }

    private Set<WorkflowExecutionStatus> allowedTargets() {
        return switch (this) {
            case QUEUED -> EnumSet.of(RUNNING, FAILED, CANCELLED);
            case RUNNING -> EnumSet.of(
                    WAITING_APPROVAL, WAITING_TIMER, WAITING_EVENT, RECOVERING, NEEDS_ATTENTION,
                    SUCCEEDED, FAILED, CANCELLED);
            case WAITING_APPROVAL -> EnumSet.of(QUEUED, FAILED, CANCELLED, REJECTED);
            case WAITING_TIMER -> EnumSet.of(QUEUED, FAILED, CANCELLED);
            case WAITING_EVENT -> EnumSet.of(QUEUED, FAILED, CANCELLED);
            case RECOVERING -> EnumSet.of(QUEUED, NEEDS_ATTENTION, FAILED, CANCELLED);
            case NEEDS_ATTENTION -> EnumSet.of(QUEUED, FAILED, CANCELLED);
            case SUCCEEDED, FAILED, CANCELLED, REJECTED -> EnumSet.noneOf(WorkflowExecutionStatus.class);
        };
    }
}
