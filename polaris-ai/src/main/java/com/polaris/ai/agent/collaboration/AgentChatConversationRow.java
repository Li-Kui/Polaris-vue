package com.polaris.ai.agent.collaboration;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AgentChatConversationRow {
    private Long id, tenantId, userId, collaborationContextStartId;
    private String collaborationContextKey;
}
