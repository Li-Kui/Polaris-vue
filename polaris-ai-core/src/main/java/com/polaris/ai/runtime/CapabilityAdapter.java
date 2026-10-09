package com.polaris.ai.runtime;

import com.polaris.ai.runtime.stream.ModelStreamEvent;

import java.util.concurrent.Flow;

/** 描述一种强类型 Capability 如何执行；Provider/Protocol 细节由实现封装。 */
public interface CapabilityAdapter<I extends CapabilityInvocation, R> {

    String capabilityCode();

    Class<I> invocationType();

    Class<R> resultType();

    ModelExecutionResult<R> execute(ModelRuntimeSpec runtime, I invocation);

    default Flow.Publisher<ModelStreamEvent> stream(
            ModelRuntimeSpec runtime,
            I invocation) {
        throw new UnsupportedOperationException(
                "Capability Adapter 不支持流式调用: " + capabilityCode());
    }
}
