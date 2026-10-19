package com.polaris.ai.agent.collaboration;

import lombok.Getter;
import lombok.Setter;

/** 已按真实会话归属选出的候选正文，不读取附件/思考过程内容。 */
@Getter @Setter
public class AgentChatHistoryRow {
    private Long id, conversationId;
    private String role, content, moderationStatus, agentRunId, chatContextKey, historyDependencies, generationStatus;
    private Boolean hasAttachments;
}
