package com.polaris.ai.workflow.runtime;

import com.polaris.ai.workflow.mapper.WorkflowExecutionMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkflowChildWakeup {
    private final WorkflowExecutionMapper executions;
    private final com.polaris.ai.workflow.service.WorkflowExecutionService service;
    public WorkflowChildWakeup(WorkflowExecutionMapper executions, com.polaris.ai.workflow.service.WorkflowExecutionService service) {
        this.executions = executions; this.service = service;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChildChanged(WorkflowChildChanged event) {
        if (event.terminal()) service.cancelChildrenOfStoppedExecution(event.executionId());
        if (event.parentExecutionId() != null) executions.wakeForChild(event.parentExecutionId());
    }
}
