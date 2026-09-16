package com.polaris.ai.workflow.application;

/** 工作流持久化事件提交后，通知本机观察通道立即读取并推送增量。 */
public record WorkflowExecutionEventAvailable(
        String executionId, long sequenceNo, String eventType) {
}
