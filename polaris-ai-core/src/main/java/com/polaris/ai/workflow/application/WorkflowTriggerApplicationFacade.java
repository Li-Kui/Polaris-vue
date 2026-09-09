package com.polaris.ai.workflow.application;

import java.util.List;

/** 管理端和中台入口共用的触发器管理边界。 */
public interface WorkflowTriggerApplicationFacade {

    List<WorkflowTriggerView> list(Long definitionId);

    WorkflowTriggerView create(WorkflowTriggerCommand command);

    WorkflowTriggerView updateStatus(
            String triggerId, WorkflowTriggerStatusCommand command);

    WorkflowExecutionView invoke(
            String triggerId, WorkflowTriggerInvocationCommand command);
}
