package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.RuntimePolicySpec;

/** 将 nullable 策略层逐字段解析为运行时策略。 */
public interface RuntimePolicyResolver {

    RuntimePolicySpec resolve(RuntimePolicyLayers layers);
}
