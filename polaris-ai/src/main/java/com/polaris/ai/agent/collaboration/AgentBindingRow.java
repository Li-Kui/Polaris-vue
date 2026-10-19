package com.polaris.ai.agent.collaboration;

import lombok.Data;
import lombok.ToString;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.ToolBinding;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.ToolSnapshot;

/** 数据库存储绑定配置，不接受客户端提供审批快照。 */
@Data
public class AgentBindingRow {
    private Long tenantId, agentId, toolId, toolRevision, connectionRevision, catalogRevision;
    private String alias, purpose, queryArgument, structureHash;
    @ToString.Exclude private String fixedArgumentsJson;
    @ToString.Exclude private String inputSchemaJson;
    public ToolBinding binding() {
        return new ToolBinding(alias, new ToolSnapshot(toolId, toolRevision, connectionRevision,
                catalogRevision, structureHash), purpose, inputSchemaJson, queryArgument, fixedArgumentsJson);
    }
}
