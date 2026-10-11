package com.polaris.platform.workflow;

import com.polaris.ai.workflow.contract.WorkflowMcpNodeContract;
import com.polaris.ai.workflow.spi.*;
import com.polaris.platform.dto.McpInvokeRequest;
import com.polaris.platform.mcp.McpInvocationException;
import com.polaris.platform.service.IPlatformMcpInvocationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 显式外部工具调用；不将远端只读注解当作幂等、重放或授权保证。 */
@Configuration
public class PlatformMcpWorkflowNodeConfig {
    @Bean
    public WorkflowNodeHandler mcpToolWorkflowNodeHandler(IPlatformMcpInvocationService invocations) {
        var descriptor = new WorkflowNodeDescriptor(WorkflowMcpNodeContract.TYPE, WorkflowMcpNodeContract.VERSION,
                "MCP 工具调用", "integration", WorkflowMcpNodeContract.configSchema(),
                WorkflowMcpNodeContract.objectSchema(), WorkflowMcpNodeContract.outputSchema(null),
                // 保守使用 WRITE：失去租约、超时或取消后禁止引擎自动重放。
                WorkflowSideEffect.WRITE, Set.of(WorkflowMcpNodeContract.RESOURCE_KIND),
                Set.of(WorkflowNodeCapability.CANCELLABLE));
        return new WorkflowNodeHandler() {
            @Override public WorkflowNodeDescriptor descriptor() { return descriptor; }

            @Override public WorkflowNodeResult execute(WorkflowNodeContext context) {
                context.cancellation().throwIfCancellationRequested();
                McpWorkflowToolHandle handle;
                try {
                    var resource = onlyTool(context.resources());
                    handle = (McpWorkflowToolHandle) resource.handle();
                    requireSnapshot(context.config(), handle);
                } catch (IllegalArgumentException invalid) {
                    throw new WorkflowNodeExecutionException("MCP_POLICY_CHANGED",
                            "MCP工具绑定或审批版本已变化，尚未派发工具调用；请重新确认并发布", true);
                }
                var request = new McpInvokeRequest();
                request.setRevision(handle.revision());
                request.setConnectionRevision(handle.connectionRevision());
                request.setCatalogRevision(handle.catalogRevision());
                request.setStructureHash(handle.structureHash());
                request.setArguments(context.input());
                var result = invocations.invokeWorkflow(handle.toolId(), request, context);
                if (!"SUCCESS".equals(result.status())) {
                    McpInvocationException.Code code;
                    try { code = McpInvocationException.Code.valueOf(result.errorCode()); }
                    catch (IllegalArgumentException | NullPointerException invalid) { code = McpInvocationException.Code.INTERNAL_ERROR; }
                    boolean notDispatched = Boolean.FALSE.equals(result.callDispatched());
                    throw new WorkflowNodeExecutionException("MCP_" + code.name(),
                            (notDispatched ? "MCP工具调用尚未派发：" : "MCP工具调用结果未确认：")
                                    + failureMessage(code, notDispatched)
                                    + "；请核对调用记录，调用ID：" + result.invocationId(), notDispatched);
                }
                context.cancellation().throwIfCancellationRequested();
                ObjectNode output = JsonNodeFactory.instance.objectNode();
                output.put("invocationId", result.invocationId());
                output.put("status", result.status());
                output.put("durationMs", result.durationMs());
                output.put("auditRecorded", result.auditRecorded());
                output.set("content", result.content());
                if (result.structuredContent() != null) output.set("structuredContent", result.structuredContent());
                return WorkflowNodeResult.success(output, "CONFIRMED");
            }
        };
    }

    @Bean
    public WorkflowNodeSchemaResolver mcpToolWorkflowSchemaResolver() {
        return new WorkflowNodeSchemaResolver() {
            @Override public boolean supports(String type, String version) {
                return WorkflowMcpNodeContract.TYPE.equals(type) && WorkflowMcpNodeContract.VERSION.equals(version);
            }

            @Override public ResolvedNodeSchema resolve(WorkflowNodeSchemaContext context) {
                try {
                    var resource = onlyTool(context.resources());
                    var handle = (McpWorkflowToolHandle) resource.handle();
                    requireSnapshot(context.config(), handle);
                    return new ResolvedNodeSchema((JsonNode) resource.attributes().get("inputSchema"),
                            (JsonNode) resource.attributes().get("outputSchema"), WorkflowMcpNodeContract.RESOURCE_KIND,
                            WorkflowMcpNodeContract.snapshotVersion(handle.snapshot()),
                            Map.of("$", WorkflowMcpNodeContract.RESOURCE_KIND), List.of());
                } catch (RuntimeException ignored) {
                    return new ResolvedNodeSchema(context.declaredInputSchema(), context.declaredOutputSchema(),
                            "NODE_CONTRACT", context.handlerVersion(), Map.of(),
                            List.of("MCP工具未绑定、不可用或快照已变化，请明确采用当前已审批版本"));
                }
            }
        };
    }

    private ResolvedWorkflowResource onlyTool(Map<String, ResolvedWorkflowResource> resources) {
        if (resources.size() != 1) throw new IllegalArgumentException("MCP节点必须且只能绑定一个工具");
        var resource = resources.values().iterator().next();
        if (!WorkflowMcpNodeContract.RESOURCE_KIND.equals(resource.kind())
                || !(resource.handle() instanceof McpWorkflowToolHandle handle)
                || !String.valueOf(handle.toolId()).equals(resource.resourceId())) {
            throw new IllegalArgumentException("MCP工具资源身份无效");
        }
        return resource;
    }

    private void requireSnapshot(JsonNode config, McpWorkflowToolHandle handle) {
        if (!WorkflowMcpNodeContract.snapshotVersion(config).equals(WorkflowMcpNodeContract.snapshotVersion(handle.snapshot()))) {
            throw new IllegalArgumentException("MCP工具或审批版本已变化，请重新确认并发布");
        }
    }

    private String failureMessage(McpInvocationException.Code code, boolean notDispatched) {
        if (!notDispatched) return code.message();
        return switch (code) {
            case TIMEOUT -> "工具调用准备阶段超时";
            case CANCELLED -> "工具调用准备阶段已取消";
            default -> code.message();
        };
    }
}
