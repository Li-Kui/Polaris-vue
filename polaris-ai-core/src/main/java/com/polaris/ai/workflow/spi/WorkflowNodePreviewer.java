package com.polaris.ai.workflow.spi;

/** 为会产生持久化副作用的节点提供不落盘预览。 */
public interface WorkflowNodePreviewer {

    WorkflowNodeResult preview(WorkflowNodeContext context) throws Exception;
}
