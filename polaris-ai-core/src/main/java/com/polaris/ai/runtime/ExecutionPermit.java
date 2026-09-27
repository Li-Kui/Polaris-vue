package com.polaris.ai.runtime;

/** 完成、失败、取消与超时路径均必须关闭的执行许可。 */
@FunctionalInterface
public interface ExecutionPermit extends AutoCloseable {

    @Override
    void close();
}
