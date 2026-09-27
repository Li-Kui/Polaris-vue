package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.RuntimePolicySpec;

/** Model/Capability 维度的 QPS 与在途并发限制器。 */
public interface ModelExecutionLimiter {

    ModelExecutionPermit acquire(
            Long modelId,
            String capabilityCode,
            RuntimePolicySpec policy);
}
