package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.RuntimePolicySpec;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.function.Function;

/** Capability non-null > Model non-null > System default，逐字段解析。 */
@Component
public class DefaultRuntimePolicyResolver implements RuntimePolicyResolver {

    static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    static final int DEFAULT_READ_TIMEOUT_MS = 60_000;
    static final int DEFAULT_RETRY_COUNT = 0;
    static final int DEFAULT_PRIORITY = 0;

    @Override
    public RuntimePolicySpec resolve(RuntimePolicyLayers layers) {
        RuntimePolicyLayers value = layers == null
                ? new RuntimePolicyLayers(null, null) : layers;
        Integer maxConcurrency = choose(
                value, RuntimePolicyLayer::maxConcurrency, null);
        Integer connectTimeoutMs = choose(
                value, RuntimePolicyLayer::connectTimeoutMs,
                DEFAULT_CONNECT_TIMEOUT_MS);
        Integer readTimeoutMs = choose(
                value, RuntimePolicyLayer::readTimeoutMs,
                DEFAULT_READ_TIMEOUT_MS);
        Integer retryCount = choose(
                value, RuntimePolicyLayer::retryCount,
                DEFAULT_RETRY_COUNT);
        BigDecimal qpsLimit = choose(
                value, RuntimePolicyLayer::qpsLimit, null);
        Integer priority = choose(
                value, RuntimePolicyLayer::priority, DEFAULT_PRIORITY);
        try {
            return new RuntimePolicySpec(
                    maxConcurrency, connectTimeoutMs, readTimeoutMs,
                    retryCount, qpsLimit, priority);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("MODEL_RUNTIME_POLICY_INVALID");
        }
    }

    private <T> T choose(
            RuntimePolicyLayers layers,
            Function<RuntimePolicyLayer, T> getter,
            T systemDefault) {
        T capability = getter.apply(layers.capability());
        if (capability != null) {
            return capability;
        }
        T model = getter.apply(layers.model());
        return model != null ? model : systemDefault;
    }
}
