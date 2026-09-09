package com.polaris.ai.workflow.application;

import java.util.List;

/** 外部事件总线适配器调用的工作流应用边界。 */
public interface WorkflowExternalEventApplicationFacade {

    List<WorkflowExecutionView> dispatch(WorkflowExternalEventCommand command);
}
