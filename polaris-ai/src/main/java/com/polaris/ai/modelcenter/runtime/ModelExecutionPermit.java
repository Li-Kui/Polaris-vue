package com.polaris.ai.modelcenter.runtime;

import com.polaris.ai.runtime.ExecutionPermit;

/** 必须在完成、失败、取消和超时路径关闭的并发许可。 */
@FunctionalInterface
public interface ModelExecutionPermit extends ExecutionPermit {

    @Override
    void close();
}
