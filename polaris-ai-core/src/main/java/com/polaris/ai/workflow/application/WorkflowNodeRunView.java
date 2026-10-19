package com.polaris.ai.workflow.application;

import java.util.Date;
import java.util.List;

/** 用于执行诊断且对调用方安全的节点尝试视图。 */
public record WorkflowNodeRunView(
        String nodeRunId,
        String nodeId,
        String branchPath,
        Integer attemptNo,
        String handlerVersion,
        String status,
        String sideEffect,
        String sideEffectStatus,
        String outputJson,
        String errorCode,
        String errorMessage,
        Date startTime,
        Date finishTime,
        String nodeName,
        String nodeType,
        List<Resource> resources) {
    public record Resource(String kind, String key) {}
}
