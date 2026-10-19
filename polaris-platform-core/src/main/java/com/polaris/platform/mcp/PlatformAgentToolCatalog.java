package com.polaris.platform.mcp;

import com.polaris.ai.agent.collaboration.AgentToolCatalog;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.platform.dto.McpToolResponse;
import com.polaris.platform.mapper.PlatformMcpConnectionMapper;
import com.polaris.platform.mapper.PlatformMcpToolMapper;
import com.polaris.platform.workflow.PlatformMcpWorkflowResourceProvider;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

@Component
public class PlatformAgentToolCatalog implements AgentToolCatalog {
    private final PlatformMcpToolMapper tools;
    private final PlatformMcpConnectionMapper connections;
    private final PlatformMcpWorkflowResourceProvider actors;
    public PlatformAgentToolCatalog(PlatformMcpToolMapper tools, PlatformMcpConnectionMapper connections,
            PlatformMcpWorkflowResourceProvider actors) {
        this.tools = tools; this.connections = connections; this.actors = actors;
    }
    @Override public CallerContext requireActor(Long tenantId, Long userId) {
        try { return actors.requireActor(tenantId, "PLATFORM_USER", String.valueOf(userId)); }
        catch (RuntimeException denied) { throw new Failure(Code.ACCESS_DENIED); }
    }
    @Override public CatalogTool resolve(Long tenantId, Long userId, Long toolId) {
        requireActor(tenantId, userId);
        var tool = tools.selectById(tenantId, toolId);
        if (tool == null || !tenantId.equals(tool.getTenantId())) throw new Failure(Code.ACCESS_DENIED);
        var connection = connections.selectById(tenantId, tool.getConnectionId());
        if (connection == null || !tenantId.equals(connection.getTenantId())) throw new Failure(Code.ACCESS_DENIED);
        var response = McpToolResponse.from(tool, connection);
        if (!response.policyReady()) throw new Failure(Code.SNAPSHOT_CHANGED);
        return new CatalogTool(connection.getConnectionName() + " / " + tool.getToolName(),
                new ToolSnapshot(toolId, response.revision(), response.connectionRevision(),
                        response.catalogRevision(), response.structureHash()), tool.getInputSchema());
    }
    @Override public List<CatalogTool> list(Long tenantId, Long userId) {
        requireActor(tenantId, userId);
        return tools.selectWorkflowResources(tenantId).stream().flatMap(tool -> {
            try { return java.util.stream.Stream.of(resolve(tenantId, userId, tool.getId())); }
            catch (Failure unavailable) { return java.util.stream.Stream.empty(); }
        }).toList();
    }
}
