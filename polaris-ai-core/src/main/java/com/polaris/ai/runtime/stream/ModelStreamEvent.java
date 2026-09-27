package com.polaris.ai.runtime.stream;

/** Runtime 内部流事件，不绑定 Legacy 或 OpenAI SSE 格式。 */
public sealed interface ModelStreamEvent permits
        TextDelta, ReasoningDelta, ToolCallDelta, UsageEvent,
        CompletedEvent, ErrorEvent, AudioMetadata, AudioChunk {
}
