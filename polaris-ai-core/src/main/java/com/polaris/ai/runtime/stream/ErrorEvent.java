package com.polaris.ai.runtime.stream;

/** 已脱敏的流式错误终态。 */
public record ErrorEvent(
        String errorCategory,
        String errorCode,
        boolean retryable) implements ModelStreamEvent {
}
