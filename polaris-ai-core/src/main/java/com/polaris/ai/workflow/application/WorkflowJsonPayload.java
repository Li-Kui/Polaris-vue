package com.polaris.ai.workflow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 在 HTTP 通用 JSON 对象与工作流内部 Jackson 2 树模型之间转换。 */
public final class WorkflowJsonPayload {

    private WorkflowJsonPayload() {
    }

    public static JsonNode toJsonNode(Object value, ObjectMapper objectMapper) {
        if (value == null) return null;
        if (value instanceof JsonNode jsonNode) return jsonNode;
        return objectMapper.valueToTree(value);
    }
}
