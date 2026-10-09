package com.polaris.ai.runtime;

/** 按 Capability + Invocation Type 查找强类型 Adapter。 */
public interface CapabilityAdapterRegistry {

    <I extends CapabilityInvocation, R> CapabilityAdapter<I, R> getRequired(
            I invocation,
            Class<R> resultType);

    <I extends CapabilityInvocation> CapabilityAdapter<I, ?> getRequired(
            I invocation);
}
