package com.polaris.ai.agent.collaboration;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 服务端构建的入口身份；工作流围栏与聊天围栏不能互相替代。 */
public sealed interface AgentEntryIdentity permits AgentEntryIdentity.Workflow, AgentEntryIdentity.Chat {
    record Workflow(String executionId, String nodeRunId, int attemptNo, String workflowCode) implements AgentEntryIdentity {
        public Workflow { id(executionId); id(nodeRunId); id(workflowCode); if (attemptNo != 1) throw new Failure(Code.INVALID_REQUEST); }
    }
    record Chat(Long conversationId, String requestId) implements AgentEntryIdentity {
        public Chat { if (conversationId == null || conversationId <= 0) throw new Failure(Code.INVALID_REQUEST); id(requestId); }
    }
    private static void id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,64}")) throw new Failure(Code.INVALID_REQUEST);
    }
}
