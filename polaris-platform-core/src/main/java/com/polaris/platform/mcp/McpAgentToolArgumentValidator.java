package com.polaris.platform.mcp;

import com.polaris.ai.agent.collaboration.AgentCollaborationPorts.ToolArgumentValidator;
import com.polaris.platform.dto.McpConnectionProbeResult.ToolPreview;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 有限计划复用 MCP 当前完整 Schema 校验；不访问连接、凭证或远端。 */
@org.springframework.stereotype.Component
public final class McpAgentToolArgumentValidator implements ToolArgumentValidator {
    private final McpToolContractPolicy policy;
    private final JsonMapper json = JsonMapper.shared();
    public McpAgentToolArgumentValidator(McpToolContractPolicy policy) { this.policy = Objects.requireNonNull(policy); }
    @Override public void validateDefinition(String schemaJson) {
        try {
            Map<String, Object> schema = json.readValue(schemaJson, new TypeReference<>() { });
            var contract = policy.prepare(new ToolPreview("agent_binding", null, null, schema,
                    null, true, false, Map.of()));
            if (!contract.supported()) throw new Failure(Code.UNSUPPORTED_SCHEMA);
        } catch (RuntimeException invalid) { throw new Failure(Code.UNSUPPORTED_SCHEMA); }
    }
    @Override public void validateArguments(String schemaJson, String argumentsJson) {
        try { policy.validateValue(schemaJson, json.readTree(argumentsJson), false); }
        catch (RuntimeException invalid) { throw new Failure(Code.ARGUMENTS_REJECTED); }
    }
}
