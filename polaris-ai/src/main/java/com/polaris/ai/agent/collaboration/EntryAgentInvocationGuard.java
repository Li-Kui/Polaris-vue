package com.polaris.ai.agent.collaboration;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 入口显式分派，不用聊天租约绕过工作流围栏。 */
@Component @Primary
public class EntryAgentInvocationGuard implements AgentInvocationGuard {
    private final PersistentAgentRunLedger workflow;
    private final ChatAgentRunLedger chat;
    public EntryAgentInvocationGuard(PersistentAgentRunLedger workflow,ChatAgentRunLedger chat) {this.workflow=workflow;this.chat=chat;}
    @Override public void checkReserved(ToolIntent intent) {
        if(intent==null)throw new Failure(Code.ACCESS_DENIED);
        if(intent.context().entry() instanceof AgentEntryIdentity.Chat)chat.checkReserved(intent);
        else workflow.checkReserved(intent);
    }
}
