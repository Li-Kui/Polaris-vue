package com.polaris.ai.workflow.spi;

/** 声明节点是否会产生外部可见副作用。 */
public enum WorkflowSideEffect {
    NONE,
    READ,
    /** 写入平台内部、受权限保护且具备幂等语义的持久化数据。 */
    DURABLE_INTERNAL,
    WRITE
}
