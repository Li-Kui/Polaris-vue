package com.polaris.ai.workflow.spi;

/** 单个带版本工作流节点实现的插件契约。 */
public interface WorkflowNodeHandler {

    WorkflowNodeDescriptor descriptor();

    WorkflowNodeResult execute(WorkflowNodeContext context) throws Exception;
}
