package com.polaris.ai.workflow.spi;

/** 根据节点配置和已授权资源解析更精确的输入输出 Schema。 */
public interface WorkflowNodeSchemaResolver {

    boolean supports(String nodeType, String handlerVersion);

    ResolvedNodeSchema resolve(WorkflowNodeSchemaContext context);
}
