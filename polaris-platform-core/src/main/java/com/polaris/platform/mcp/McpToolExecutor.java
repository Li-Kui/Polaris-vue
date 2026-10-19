package com.polaris.platform.mcp;

import com.polaris.platform.domain.PlatformMcpConnection;
import com.polaris.ai.agent.collaboration.AgentCallControl;
import com.polaris.platform.domain.PlatformMcpTool;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;

/** 每次调用重新发现并核对远端定义；只发出一次tools/call，不自动重试。 */
@Component
public class McpToolExecutor {
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};
    private final McpConnectionProbe sessions;
    private final McpToolContractPolicy contracts;

    public McpToolExecutor(McpConnectionProbe sessions, McpToolContractPolicy contracts) {
        this.sessions = sessions;
        this.contracts = contracts;
    }

    public Result execute(PlatformMcpConnection connection, PlatformMcpTool tool,
            JsonNode arguments, Runnable beforeCall) {
        return execute(connection, tool, arguments, beforeCall, null);
    }

    public Result execute(PlatformMcpConnection connection, PlatformMcpTool tool,
            JsonNode arguments, Runnable beforeCall, AgentCallControl control) {
        return sessions.withSession(connection, true, true, (client, initialized, discovered, deadline, started) -> {
            var remote = discovered.stream().filter(next -> tool.getToolName().equals(next.name())).findFirst()
                    .orElseThrow(() -> new McpInvocationException(McpInvocationException.Code.DEFINITION_CHANGED));
            var current = contracts.prepare(remote);
            if (!current.supported() || !Objects.equals(current.structureHash(), tool.getStructureHash())) {
                throw new McpInvocationException(McpInvocationException.Code.DEFINITION_CHANGED);
            }
            // 初始化/发现期间发生撤销或连接变更时，不能使用开始时的审批继续发起调用。
            beforeCall.run();
            Map<String, Object> parameters = JSON.convertValue(arguments, OBJECT);
            McpSchema.CallToolResult result = McpConnectionProbe.await(client.callTool(
                    new McpSchema.CallToolRequest(tool.getToolName(), parameters, null)), deadline, control);
            if (result == null || result.content() == null || result.content().stream().anyMatch(Objects::isNull)) {
                throw new McpInvocationException(McpInvocationException.Code.PROTOCOL_ERROR);
            }
            if (JSON.writeValueAsBytes(result).length > 256 * 1024) {
                throw new McpInvocationException(McpInvocationException.Code.RESULT_TOO_LARGE);
            }
            boolean error = Boolean.TRUE.equals(result.isError());
            if (!error && tool.getOutputSchema() != null) {
                contracts.validateValue(tool.getOutputSchema(), result.structuredContent(), true);
            }
            JsonNode structured = result.structuredContent() == null ? null : JSON.valueToTree(result.structuredContent());
            if (structured != null && !structured.isObject()) {
                throw new McpInvocationException(McpInvocationException.Code.PROTOCOL_ERROR);
            }
            return new Result(error, JSON.valueToTree(result.content()), structured);
        }, control);
    }

    public record Result(boolean isError, JsonNode content, JsonNode structuredContent) {
        @Override public String toString() { return "McpToolResult[isError=" + isError + "]"; }
    }
}
