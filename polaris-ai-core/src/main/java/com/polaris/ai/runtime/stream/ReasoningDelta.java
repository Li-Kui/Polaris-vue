package com.polaris.ai.runtime.stream;

/** 推理内容增量。 */
public record ReasoningDelta(String text) implements ModelStreamEvent {
}
