package com.polaris.ai.workflow.application;

/** 稳定的本地投递信封；下游消费者必须按事件ID去重。 */
public record WorkflowOutboxEvent(
        String eventId,
        Long tenantId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson) {
}
