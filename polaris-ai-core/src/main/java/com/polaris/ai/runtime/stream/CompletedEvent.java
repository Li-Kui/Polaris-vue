package com.polaris.ai.runtime.stream;

/** 流式调用的业务终态。 */
public record CompletedEvent(
        String finishReason,
        String providerRequestId) implements ModelStreamEvent {
}
