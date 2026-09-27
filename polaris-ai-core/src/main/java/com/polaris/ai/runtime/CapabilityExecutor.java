package com.polaris.ai.runtime;

import com.polaris.ai.runtime.stream.ModelStreamEvent;

import java.util.concurrent.Flow;

/** 强类型 Capability 的统一执行入口。 */
public interface CapabilityExecutor {

    <I extends CapabilityInvocation, R> ModelExecutionResult<R> execute(
            ModelRuntimeSpec runtime,
            I invocation,
            Class<R> resultType);

    <I extends CapabilityInvocation> Flow.Publisher<ModelStreamEvent> stream(
            ModelRuntimeSpec runtime,
            I invocation);
}
