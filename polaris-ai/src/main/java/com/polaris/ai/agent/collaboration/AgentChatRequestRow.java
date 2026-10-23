package com.polaris.ai.agent.collaboration;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** 不含请求/模型/工具正文；禁止默认生成包含内部 token 的 toString。 */
@Getter @Setter
public class AgentChatRequestRow {
    private String requestId, requestKey, inputHash, snapshotHash, snapshotJson, historyHash, historyJson, contextKey;
    private Long tenantId, actorUserId, conversationId, contextStartMessageId, historyWatermark, agentId, modelId, knowledgeBaseId;
    private String answerMode, status, runId, ownerToken, errorCode;
    private Long userMessageId, assistantMessageId;
    private Integer maxCalls, maxWaitSeconds;
    private Date leaseUntil, cancelRequestedAt, expiresAt, deadline, createdTime, startedTime, finishedTime;
}
