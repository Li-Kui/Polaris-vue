package com.polaris.ai.agent.collaboration;

import lombok.Data;

import java.util.Date;

/** 不含模型或工具正文的运行元数据。 */
@Data
public class AgentRunRow {
    private String runId, executionId, nodeRunId, workflowCode, runnerId, definitionHash, modelHash, requestHash;
    private Long tenantId, actorUserId, agentId, modelId, fencingToken;
    private Integer attemptNo, maxCalls, toolCount, planCount, answerCount;
    private Boolean planUsageRecorded, answerUsageRecorded;
    private Long planInputTokens, planOutputTokens, planTotalTokens, answerInputTokens, answerOutputTokens, answerTotalTokens;
    private String status, stage, errorCode, dispatch;
    private Date deadline, startedTime, finishedTime;
}
