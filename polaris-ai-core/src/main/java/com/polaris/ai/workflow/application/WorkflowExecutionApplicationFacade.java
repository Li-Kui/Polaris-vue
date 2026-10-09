package com.polaris.ai.workflow.application;

import java.util.List;

/** 工作流持久化执行操作的应用边界。 */
public interface WorkflowExecutionApplicationFacade {

    WorkflowExecutionView start(WorkflowExecutionStartCommand command);

    WorkflowExecutionView startByCode(WorkflowExecutionByCodeCommand command);

    WorkflowExecutionView get(String executionId);

    List<WorkflowExecutionView> list(Long definitionId, String status);

    List<WorkflowNodeRunView> listNodeRuns(String executionId);

    List<WorkflowExecutionEventView> listEvents(String executionId, long afterSequence, int limit);

    WorkflowExecutionView cancel(String executionId);

    WorkflowExecutionView retry(String executionId, WorkflowExecutionRetryCommand command);

    /** 获取分享运行时所需的已发布定义和输入 Schema。 */
    WorkflowShareDefinitionView getShareDefinition(Long definitionId);
}
