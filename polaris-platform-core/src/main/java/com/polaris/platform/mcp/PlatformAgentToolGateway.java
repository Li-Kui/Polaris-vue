package com.polaris.platform.mcp;

import com.polaris.ai.agent.collaboration.*;
import com.polaris.platform.service.impl.PlatformMcpInvocationServiceImpl;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 共用 MCP 的审批、远端定义复查、限流与审计；没有 AiServices 工具循环。 */
@Component
public class PlatformAgentToolGateway implements AgentCollaborationPorts.ExternalReadToolGateway {
    private final AgentToolCatalog catalog;
    private final PlatformMcpInvocationServiceImpl invocations;
    public PlatformAgentToolGateway(AgentToolCatalog catalog, PlatformMcpInvocationServiceImpl invocations) {
        this.catalog = catalog; this.invocations = invocations;
    }
    @Override public void validateBindings(Context context, List<ToolBinding> bindings) {
        catalog.requireActor(context.tenantId(), context.actorUserId());
        for (ToolBinding binding : bindings) {
            var current = catalog.resolve(context.tenantId(), context.actorUserId(), binding.snapshot().toolId());
            if (!current.snapshot().equals(binding.snapshot()) || !JsonMapper.shared().readTree(current.inputSchemaJson())
                    .equals(JsonMapper.shared().readTree(binding.inputSchemaJson()))) throw new Failure(Code.SNAPSHOT_CHANGED);
        }
    }
    @Override public ToolResult invoke(ToolIntent intent, AgentCallControl control) {
        var result = invocations.invokeAgent(intent, control);
        Dispatch dispatch = result.callDispatched() == null ? Dispatch.UNKNOWN
                : Boolean.TRUE.equals(result.callDispatched()) ? Dispatch.PREPARED : Dispatch.NOT_DISPATCHED;
        var body = JsonMapper.shared().createObjectNode();
        if (result.content() != null) body.set("content", result.content());
        if (result.structuredContent() != null) body.set("structuredContent", result.structuredContent());
        String content = body.isEmpty() ? null : JsonMapper.shared().writeValueAsString(body);
        return new ToolResult(result.invocationId(), "SUCCESS".equals(result.status()), result.auditRecorded(), dispatch, content, false);
    }
}
