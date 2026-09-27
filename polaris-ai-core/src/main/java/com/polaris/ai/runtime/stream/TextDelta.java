package com.polaris.ai.runtime.stream;

/** 文本增量。 */
public record TextDelta(String text) implements ModelStreamEvent {
}
