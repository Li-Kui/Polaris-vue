package com.polaris.platform.dto;

import tools.jackson.databind.JsonNode;

/** 结果仅返回当前调用者，不持久化；失败不伪装为远端调用成功。 */
public record McpInvokeResult(String invocationId, String status, long durationMs, String errorCode,
        String message, boolean isError, JsonNode content, JsonNode structuredContent, boolean auditRecorded,
        Boolean callDispatched) {
    /** 旧调用者没有派发证据时保持未知，不默认解释成未派发。 */
    public McpInvokeResult(String invocationId, String status, long durationMs, String errorCode,
            String message, boolean isError, JsonNode content, JsonNode structuredContent, boolean auditRecorded) {
        this(invocationId, status, durationMs, errorCode, message, isError, content, structuredContent, auditRecorded, null);
    }

    @Override public String toString() {
        return "McpInvokeResult[invocationId=" + invocationId + ", status=" + status + ", errorCode=" + errorCode + "]";
    }
}
