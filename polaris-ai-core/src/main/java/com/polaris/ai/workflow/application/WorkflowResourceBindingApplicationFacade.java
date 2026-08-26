package com.polaris.ai.workflow.application;

import java.util.List;

/** 当前所有者资源绑定管理的应用边界。 */
public interface WorkflowResourceBindingApplicationFacade {

    List<WorkflowResourceBindingView> list(String environment);

    WorkflowResourceBindingView save(WorkflowResourceBindingCommand command);

    WorkflowResourceBindingView disable(Long bindingId);
}
