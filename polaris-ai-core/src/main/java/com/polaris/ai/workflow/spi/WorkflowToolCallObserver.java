package com.polaris.ai.workflow.spi;

/** 接收不含工具参数和返回内容的工作流工具调用审计事件。 */
@FunctionalInterface
public interface WorkflowToolCallObserver {

    WorkflowToolCallObserver NONE = event -> { };

    void onEvent(Event event);

    record Event(
            String toolName,
            int callNo,
            Status status,
            long durationMs,
            String reasonCode,
            boolean resultTruncated) {

        public Event {
            toolName = toolName == null ? "unknown" : toolName;
            callNo = Math.max(1, callNo);
            durationMs = Math.max(0, durationMs);
        }
    }

    enum Status {
        STARTED,
        SUCCEEDED,
        FAILED,
        BLOCKED
    }
}
