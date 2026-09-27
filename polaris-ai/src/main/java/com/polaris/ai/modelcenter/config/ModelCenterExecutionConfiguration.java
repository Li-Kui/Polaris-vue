package com.polaris.ai.modelcenter.config;

import com.polaris.ai.modelcenter.runtime.ModelExecutionLimiter;
import com.polaris.ai.runtime.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 装配纯 Core Executor 与 Model Center 的分布式执行许可。 */
@Configuration
public class ModelCenterExecutionConfiguration {

    @Bean
    public CapabilityAdapterRegistry capabilityAdapterRegistry(
            List<CapabilityAdapter<?, ?>> adapters) {
        return new DefaultCapabilityAdapterRegistry(adapters);
    }

    @Bean
    public ExecutionPermitProvider modelCenterExecutionPermitProvider(
            ModelExecutionLimiter limiter) {
        return runtime -> limiter.acquire(
                runtime.modelId(), runtime.capabilityCode(),
                runtime.runtimePolicy());
    }

    @Bean
    public CapabilityExecutor capabilityExecutor(
            CapabilityAdapterRegistry adapterRegistry,
            ExecutionPermitProvider permitProvider,
            List<ModelExecutionObserver> observers,
            List<ModelUsageListener> usageListeners) {
        return new DefaultCapabilityExecutor(
                adapterRegistry, permitProvider, observers, usageListeners);
    }
}
