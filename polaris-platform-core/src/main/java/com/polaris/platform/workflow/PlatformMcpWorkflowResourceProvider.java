package com.polaris.platform.workflow;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.workflow.application.WorkflowResourceOption;
import com.polaris.ai.workflow.contract.WorkflowMcpNodeContract;
import com.polaris.ai.workflow.spi.ResolvedWorkflowResource;
import com.polaris.ai.workflow.spi.WorkflowResourceCatalogRequest;
import com.polaris.ai.workflow.spi.WorkflowResourceProvider;
import com.polaris.ai.workflow.spi.WorkflowResourceRequest;
import com.polaris.platform.auth.PlatformWorkflowPrincipalContextProvider;
import com.polaris.platform.dto.McpToolResponse;
import com.polaris.platform.mapper.PlatformMcpConnectionMapper;
import com.polaris.platform.mapper.PlatformMcpToolMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/** 编排、发布及恢复阶段均按实际执行主体重新校验，不信任请求线程残留身份。 */
@Component
public class PlatformMcpWorkflowResourceProvider implements WorkflowResourceProvider {
    private final PlatformMcpConnectionMapper connections;
    private final PlatformMcpToolMapper tools;
    private final PlatformWorkflowPrincipalContextProvider principals;

    public PlatformMcpWorkflowResourceProvider(PlatformMcpConnectionMapper connections,
            PlatformMcpToolMapper tools, PlatformWorkflowPrincipalContextProvider principals) {
        this.connections = connections;
        this.tools = tools;
        this.principals = principals;
    }

    @Override public String kind() { return WorkflowMcpNodeContract.RESOURCE_KIND; }

    public CallerContext requireActor(Long tenantId, String principalType, String principalId) {
        if (tenantId == null || tenantId <= 0 || !"PLATFORM_USER".equals(principalType)
                || principalId == null || !principalId.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("MCP工作流仅允许有效租户管理员执行");
        }
        CallerContext actor = principals.resolve(tenantId, principalType, principalId).orElseThrow(
                () -> new IllegalArgumentException("MCP工作流执行主体已失效"));
        if (!actor.isPlatformMode() || !actor.isTenantAdmin()
                || !String.valueOf(tenantId).equals(actor.getTenantId())
                || !principalId.equals(String.valueOf(actor.getUserId()))) {
            throw new IllegalArgumentException("MCP工作流执行主体无权调用工具");
        }
        return actor;
    }

    @Override public List<String> validate(WorkflowResourceRequest request) {
        try { resolve(request); return List.of(); }
        catch (RuntimeException ignored) { return List.of("MCP工具不存在、未审批、版本已变化或当前主体无权使用"); }
    }

    @Override public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
        if (request == null || !kind().equals(request.resourceKind())) throw new IllegalArgumentException("MCP资源类型无效");
        requireActor(request.tenantId(), request.principalType(), request.principalId());
        long id;
        try { id = Long.parseLong(request.resourceId()); } catch (Exception ignored) { throw new IllegalArgumentException("MCP工具身份无效"); }
        if (id <= 0) throw new IllegalArgumentException("MCP工具身份无效");
        var tool = tools.selectById(request.tenantId(), id);
        if (tool == null || !Objects.equals(tool.getTenantId(), request.tenantId())) throw new IllegalArgumentException("MCP工具不可用");
        var connection = connections.selectById(request.tenantId(), tool.getConnectionId());
        if (connection == null || !Objects.equals(connection.getTenantId(), request.tenantId())) throw new IllegalArgumentException("MCP连接不可用");
        var response = McpToolResponse.from(tool, connection);
        if (!response.policyReady() || request.resourceVersion() != null
                && request.resourceVersion().longValue() != response.revision()) throw new IllegalArgumentException("MCP工具审批或版本已失效");
        var handle = new McpWorkflowToolHandle(tool.getId(), connection.getId(), response.revision(),
                response.connectionRevision(), response.catalogRevision(), response.structureHash());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", tool.getToolTitle() == null ? tool.getToolName() : tool.getToolTitle());
        attributes.put("connectionName", connection.getConnectionName());
        attributes.put("toolName", tool.getToolName());
        attributes.put("snapshot", handle.snapshot());
        attributes.put("inputSchema", response.inputSchema());
        attributes.put("outputSchema", WorkflowMcpNodeContract.outputSchema(response.outputSchema()));
        // BIGINT 修订保存在完整 config 快照中，不能截断到 SPI 的 int resourceVersion。
        return new ResolvedWorkflowResource(kind(), request.resourceKey(), String.valueOf(id), 0, attributes, handle);
    }

    @Override public List<WorkflowResourceOption> listAvailable(WorkflowResourceCatalogRequest request) {
        try { requireActor(request.tenantId(), request.principalType(), request.principalId()); }
        catch (RuntimeException ignored) { return List.of(); }
        List<WorkflowResourceOption> result = new ArrayList<>();
        for (var tool : tools.selectWorkflowResources(request.tenantId())) {
            var reference = new WorkflowResourceRequest(request.tenantId(), request.environment(), kind(),
                    "catalog", String.valueOf(tool.getId()), request.principalType(), request.principalId());
            try {
                var resolved = resolve(reference);
                result.add(new WorkflowResourceOption(kind(), resolved.resourceId(),
                        String.valueOf(resolved.attributes().get("connectionName")) + " / " + resolved.attributes().get("name"),
                        tool.getDescription(), "0", true, null, false, resolved.attributes()));
            } catch (RuntimeException ignored) {
                result.add(new WorkflowResourceOption(kind(), String.valueOf(tool.getId()), tool.getToolName(),
                        null, "1", false, "当前版本未审批、连接不可用或工具已变化", false, Map.of()));
            }
        }
        return List.copyOf(result);
    }
}
