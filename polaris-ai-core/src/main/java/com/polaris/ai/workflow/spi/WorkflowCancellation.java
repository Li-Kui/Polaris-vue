package com.polaris.ai.workflow.spi;

/** 传递给节点处理器的协作式取消探针。 */
@FunctionalInterface
public interface WorkflowCancellation {
    WorkflowCancellation NONE = () -> false;

    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new IllegalStateException("WORKFLOW_EXECUTION_CANCELLED");
        }
    }
}
