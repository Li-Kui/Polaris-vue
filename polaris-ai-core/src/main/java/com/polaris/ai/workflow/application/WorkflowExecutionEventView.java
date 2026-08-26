package com.polaris.ai.workflow.application;

import java.util.Date;

/** 可持久化、可重放的执行事件视图。 */
public record WorkflowExecutionEventView(
        Long sequenceNo,
        String eventType,
        String nodeRunId,
        String nodeId,
        String payloadJson,
        Date createTime) {
}
