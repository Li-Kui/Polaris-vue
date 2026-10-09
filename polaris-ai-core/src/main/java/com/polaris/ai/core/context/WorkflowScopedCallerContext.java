package com.polaris.ai.core.context;

/** 可按工作流编码限制访问范围的调用主体。 */
public interface WorkflowScopedCallerContext extends CallerContext {

    boolean canAccessWorkflow(String workflowCode);
}
