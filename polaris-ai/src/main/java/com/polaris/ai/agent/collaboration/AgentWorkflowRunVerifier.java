package com.polaris.ai.agent.collaboration;

import com.polaris.ai.workflow.contract.WorkflowAgentNodeContract;
import com.polaris.ai.workflow.definition.WorkflowExecutionPlan;
import com.polaris.ai.workflow.domain.WorkflowExecution;
import com.polaris.ai.workflow.domain.WorkflowNodeRun;
import com.polaris.ai.workflow.mapper.WorkflowVersionMapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.Code;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.Failure;

/** 每次派发与流式等待均以不可变执行计划验证真实节点，不能靠调用者填写的身份授权。 */
@Component
public class AgentWorkflowRunVerifier {
    private final WorkflowVersionMapper versions;
    private final AgentKnowledgeSnapshotService knowledge;
    public AgentWorkflowRunVerifier(WorkflowVersionMapper versions, AgentKnowledgeSnapshotService knowledge) {
        this.versions = versions; this.knowledge = knowledge;
    }
    public tools.jackson.databind.JsonNode verify(AgentRunRow row, WorkflowExecution execution, WorkflowNodeRun node) {
        try {
            var version = versions.selectAgentVersion(row.getTenantId(), execution.getWorkflowVersionId());
            if (version == null || !Objects.equals(version.getTenantId(), row.getTenantId())) throw new Failure(Code.ACCESS_DENIED);
            var plan = JsonMapper.shared().readValue(version.getExecutionPlanJson(), WorkflowExecutionPlan.class);
            if (!Objects.equals(plan.getContentHash(), execution.getPlanHash()) || !Objects.equals(version.getContentHash(), execution.getPlanHash()))
                throw new Failure(Code.SNAPSHOT_CHANGED);
            var selected = plan.getNodes().stream().filter(item -> node.getNodeId().equals(item.getId())).findFirst()
                    .orElseThrow(() -> new Failure(Code.ACCESS_DENIED));
            var config = selected.getConfig(); WorkflowAgentNodeContract.snapshotVersion(config);
            if (!"agent".equals(selected.getType()) || !"2.0".equals(selected.getHandlerVersion())
                    || !"WRITE".equals(selected.getSideEffect()) || !row.getAgentId().toString().equals(config.path("agentId").asString())
                    || !WorkflowAgentNodeContract.SOURCE.equals(selected.getSchemaSource())
                    || !WorkflowAgentNodeContract.snapshotVersion(config).equals(selected.getSchemaSourceVersion())
                    || !row.getDefinitionHash().equals(config.path("definitionHash").asString()) || row.getMaxCalls() != config.path("maxCalls").asInt())
                throw new Failure(Code.SNAPSHOT_CHANGED);
            if (config.has("knowledgeId")) knowledge.requireCurrent(row.getTenantId(), row.getActorUserId(),
                    config.path("knowledgeId").asString(), config.path("knowledgeHash").asString());
            return config;
        } catch (Failure safe) { throw safe; }
        catch (RuntimeException unavailable) { throw new Failure(Code.SNAPSHOT_CHANGED); }
    }
}
