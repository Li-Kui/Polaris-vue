package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.modelcenter.client.ProviderCallException;
import com.polaris.ai.modelcenter.client.ProviderErrorType;
import com.polaris.ai.runtime.RuntimePolicySpec;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;

/** 只包围 Provider Transport Call 的保守重试器。 */
@Component
public class ProviderRetryExecutor {

    private static final Set<ProviderErrorType> RETRYABLE = Set.of(
            ProviderErrorType.RATE_LIMITED,
            ProviderErrorType.PROVIDER_TIMEOUT,
            ProviderErrorType.PROVIDER_UNAVAILABLE);

    public <T> T execute(
            RuntimePolicySpec policy,
            ProviderRetrySafety safety,
            Supplier<T> providerTransportCall) {
        if (policy == null || safety == null
                || providerTransportCall == null) {
            throw new IllegalArgumentException("Provider Retry 参数不能为空");
        }
        int retries = safety.retryAllowed() ? policy.retryCount() : 0;
        for (int attempt = 0; ; attempt++) {
            try {
                return providerTransportCall.get();
            } catch (ProviderCallException error) {
                if (attempt >= retries
                        || !RETRYABLE.contains(error.errorType())) {
                    throw error;
                }
                backoff(attempt);
            }
        }
    }

    private void backoff(int failedAttempt) {
        long delayMillis = Math.min(1_000L, 100L << failedAttempt);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(
                    ProviderErrorType.UNKNOWN, null, null, 0);
        }
    }
}
