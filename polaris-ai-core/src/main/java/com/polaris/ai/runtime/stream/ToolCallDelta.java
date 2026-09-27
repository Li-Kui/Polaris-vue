package com.polaris.ai.runtime.stream;

/** 工具调用参数增量。 */
public record ToolCallDelta(
        int index,
        String toolCallId,
        String toolName,
        String argumentsDelta,
        boolean complete) implements ModelStreamEvent {
}
