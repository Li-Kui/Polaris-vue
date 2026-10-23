package com.polaris.ai.agent.collaboration;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** MCP 委托必须已有持久化预算预留，并复查对应入口的围栏与绑定。 */
public interface AgentInvocationGuard {
    void checkReserved(ToolIntent intent);
}
