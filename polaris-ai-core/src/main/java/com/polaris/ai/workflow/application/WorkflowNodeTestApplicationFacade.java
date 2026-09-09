package com.polaris.ai.workflow.application;

/** 安全的单节点隔离试运行入口。 */
public interface WorkflowNodeTestApplicationFacade {

    WorkflowNodeTestResult create(
            Long definitionId, String nodeId, WorkflowNodeTestCommand command);

    WorkflowNodeTestResult get(String testRunId);

    WorkflowNodeTestResult cancel(String testRunId);

    WorkflowInferredSchemaResult inferSchema(String testRunId);
}
