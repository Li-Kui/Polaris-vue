package com.polaris.ai.agent.collaboration;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter @Setter
public class ChatGenerationRow {
    private Long tenantId, actorUserId, conversationId;
    private String entryType, generationId, ownerToken;
    private Date leaseUntil, deadline, cancelRequestedAt;
}
