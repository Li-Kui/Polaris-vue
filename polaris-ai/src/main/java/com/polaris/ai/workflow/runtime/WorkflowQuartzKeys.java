package com.polaris.ai.workflow.runtime;

import org.quartz.JobKey;
import org.quartz.TriggerKey;

/** 工作流 Quartz 对象使用独立命名空间，避免与通用 sys_job 相互清理。 */
final class WorkflowQuartzKeys {

    static final String GROUP = "WORKFLOW_TRIGGER";

    private WorkflowQuartzKeys() {
    }

    static JobKey jobKey(String triggerId) {
        return JobKey.jobKey("workflow-trigger-" + triggerId, GROUP);
    }

    static TriggerKey triggerKey(String triggerId) {
        return TriggerKey.triggerKey("workflow-trigger-" + triggerId, GROUP);
    }
}
