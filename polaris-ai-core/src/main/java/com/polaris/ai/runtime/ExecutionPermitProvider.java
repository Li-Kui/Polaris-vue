package com.polaris.ai.runtime;

/** 为单次模型调用获取 QPS / 并发等执行许可。 */
@FunctionalInterface
public interface ExecutionPermitProvider {

    ExecutionPermit acquire(ModelRuntimeSpec runtime);
}
