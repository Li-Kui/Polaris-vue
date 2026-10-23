package com.polaris.ai.agent.collaboration;

import lombok.Data;

@Data
public class AgentRunCallRow {
    private Long tenantId, toolId, toolRevision, connectionRevision, catalogRevision;
    private String runId, structureHash, argumentsHash, status, invocationId, dispatch;
    private Integer callNo;
}
