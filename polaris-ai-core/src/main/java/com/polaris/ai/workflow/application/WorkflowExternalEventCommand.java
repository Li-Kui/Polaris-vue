package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;

/** 外部事件总线传入工作流的标准事件命令。 */
public record WorkflowExternalEventCommand(
        Long tenantId,
        String eventType,
        String eventId,
        JsonNode payload) {
}
