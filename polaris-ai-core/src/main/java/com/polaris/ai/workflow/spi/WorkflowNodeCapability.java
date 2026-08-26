package com.polaris.ai.workflow.spi;

/** 节点实现声明的可选运行时能力。 */
public enum WorkflowNodeCapability {
    CANCELLABLE,
    RETRYABLE,
    MOCKABLE,
    STREAMING,
    CHECKPOINT_SAFE,
    IDEMPOTENCY_KEY
}
