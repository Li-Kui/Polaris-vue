package com.polaris.ai.workflow.application;

import java.time.Instant;

/** 事务提交后通知工作线程新增时间或重新读取持久化调度状态。 */
public record WorkflowTimerSignal(Instant resumeAt, boolean refresh) {

    public WorkflowTimerSignal(Instant resumeAt) {
        this(resumeAt, false);
    }

    public static WorkflowTimerSignal refreshSchedule() {
        return new WorkflowTimerSignal(null, true);
    }
}
