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
}
