package com.polaris.ai.workflow.spi;

/** 节点实现声明的可选运行时能力。 */
public enum WorkflowNodeCapability {
    CANCELLABLE,
    RETRYABLE,
    MOCKABLE,
    /** 支持不产生真实副作用的独立预览。 */
    PREVIEWABLE,
    STREAMING,
    CHECKPOINT_SAFE,
    IDEMPOTENCY_KEY
}
