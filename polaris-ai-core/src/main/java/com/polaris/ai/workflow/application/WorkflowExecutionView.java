package com.polaris.ai.workflow.application;

import java.util.Date;

/** 对调用方安全的工作流执行视图。 */
public record WorkflowExecutionView(
        String executionId,
        String parentExecutionId,
        String rootExecutionId,
        Integer executionDepth,
        Long definitionId,
        String workflowVersionId,
        String workflowCode,
        Integer versionNo,
        String status,
        String inputJson,
        String outputJson,
        String budgetJson,
        String usageJson,
        String errorCode,
        String errorMessage,
        Long eventSequence,
        Boolean cancelRequested,
        Date createTime,
        Date startTime,
        Date finishTime) {
}
