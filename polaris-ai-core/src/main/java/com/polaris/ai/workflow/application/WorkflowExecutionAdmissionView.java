package com.polaris.ai.workflow.application;

/** 已发布版本的准入提示，不创建执行，也不替代启动及派发时的复查。 */
public record WorkflowExecutionAdmissionView(
        boolean allowed, String code, String reason, String workflowVersionId) {
}
