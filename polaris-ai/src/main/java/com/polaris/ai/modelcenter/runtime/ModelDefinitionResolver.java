package com.polaris.ai.modelcenter.runtime;

/** 解析可缓存静态模型定义；每次调用仍检查当前权限与 Revision。 */
public interface ModelDefinitionResolver {

    ResolvedModelDefinition resolve(
            Long modelId,
            String invocationCapabilityCode);
}
