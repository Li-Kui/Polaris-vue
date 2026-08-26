package com.polaris.ai.workflow.spi;

/** 声明节点是否会产生外部可见副作用。 */
public enum WorkflowSideEffect {
    NONE,
    READ,
    WRITE
}
