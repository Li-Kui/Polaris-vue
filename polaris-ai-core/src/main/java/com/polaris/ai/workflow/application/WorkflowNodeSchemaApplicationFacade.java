package com.polaris.ai.workflow.application;

import java.util.List;

/** 按已保存草稿和当前环境解析节点有效 Schema。 */
public interface WorkflowNodeSchemaApplicationFacade {

    List<WorkflowResolvedNodeSchemaView> resolve(Long definitionId, String environment);
}
