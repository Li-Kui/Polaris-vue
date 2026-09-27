package com.polaris.ai.modelcenter.runtime;

import java.util.Set;

/** 将本次调用显式请求的 Feature 与模型已启用 Feature 分离。 */
public interface FeatureActivationResolver {

    Set<String> resolve(
            ResolvedModelDefinition definition,
            Set<String> requestedFeatures);
}
