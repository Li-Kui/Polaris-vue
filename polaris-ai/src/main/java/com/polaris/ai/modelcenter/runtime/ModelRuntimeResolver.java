package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.CapabilityInvocation;
import com.polaris.ai.runtime.ModelRuntimeSpec;

/** 每次请求组合动态参数并加载当前有效 Credential。 */
public interface ModelRuntimeResolver {

    <I extends CapabilityInvocation> ModelRuntimeSpec resolve(
            ModelRuntimeRequest<I> request);
}
