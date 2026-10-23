package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContext;
import java.util.List;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 本地审批目录与当前主体校验；不发现远端、不读取凭证。 */
public interface AgentToolCatalog {
    CallerContext requireActor(Long tenantId, Long actorUserId);
    CatalogTool resolve(Long tenantId, Long actorUserId, Long toolId);
    List<CatalogTool> list(Long tenantId, Long actorUserId);
    record CatalogTool(String label, ToolSnapshot snapshot, String inputSchemaJson) { }
}
