package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.mapper.AgentRunMapper;
import com.polaris.ai.workflow.mapper.WorkflowVersionMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 运行详情只返回台账元数据；无重新执行或取回远端原结果入口。 */
@Service
public class AgentRunQueryService {
    private final AgentRunMapper runs;
    private final AgentToolCatalog actors;
    private final WorkflowVersionMapper versions;
    public AgentRunQueryService(AgentRunMapper runs, AgentToolCatalog actors, WorkflowVersionMapper versions) {
        this.runs = runs; this.actors = actors; this.versions = versions;
    }
    private Long tenant() {
        try {
            var caller = CallerContextHolder.require(); Long tenant = Long.valueOf(caller.getTenantId());
            if (!caller.isPlatformMode() || !caller.isTenantAdmin()) throw new IllegalArgumentException();
            actors.requireActor(tenant, caller.getUserId()); return tenant;
        } catch (RuntimeException denied) { throw new ServiceException("仅当前租户管理员可查看协作运行", 403); }
    }
    public record ModelUsage(Long inputTokens, Long outputTokens, Long totalTokens) { }
    public record Call(Integer callNo, String toolId, String status, String invocationId, String dispatch) { }
    public record View(String runId, String executionId, String nodeRunId, String agentId, String status, String stage,
            Integer maxCalls, Integer toolCount, Integer planCount, Integer answerCount, ModelUsage planningUsage, ModelUsage answerUsage,
            String errorCode, String dispatch, Date startedTime, Date finishedTime, List<Call> calls) { }
    public View forNode(String execution, String node) {
        Long tenant = tenant();
        if (execution == null || node == null || !execution.matches("[A-Za-z0-9_.:-]{1,64}") || !node.matches("[A-Za-z0-9_.:-]{1,64}"))
            throw new ServiceException("执行或节点标识无效", 400);
        if (runs.execution(tenant, execution) == null || runs.node(tenant, execution, node) == null)
            throw new ServiceException("节点执行不存在或无权访问", 404);
        var row = runs.forNode(tenant, execution, node); if (row == null) return null;
        return new View(row.getRunId(), row.getExecutionId(), row.getNodeRunId(), row.getAgentId().toString(), row.getStatus(), row.getStage(),
                row.getMaxCalls(), row.getToolCount(), row.getPlanCount(), row.getAnswerCount(),
                new ModelUsage(row.getPlanInputTokens(), row.getPlanOutputTokens(), row.getPlanTotalTokens()),
                new ModelUsage(row.getAnswerInputTokens(), row.getAnswerOutputTokens(), row.getAnswerTotalTokens()),
                row.getErrorCode(), row.getDispatch(), row.getStartedTime(), row.getFinishedTime(),
                runs.calls(tenant, row.getRunId()).stream().map(call -> new Call(call.getCallNo(), call.getToolId().toString(), call.getStatus(), call.getInvocationId(), call.getDispatch())).toList());
    }
    public record Impact(String workflowCode, String workflowName, String scope, String versionId, List<String> nodeIds) { }
    public record ImpactView(List<Impact> items, boolean truncated, int scannedDefinitions) { }
    public ImpactView impact() {
        Long tenant = tenant(); var definitions = runs.impactCandidates(tenant); List<Impact> result = new ArrayList<>();
        for (var definition : definitions.stream().limit(200).toList()) {
            var draft = legacyNodes(definition.getDraftJson(), "typeVersion");
            if (!draft.isEmpty()) result.add(new Impact(definition.getWorkflowCode(), definition.getWorkflowName(), "DRAFT", null, draft));
            if (definition.getCurrentPublishedVersionId() != null) {
                var version = versions.selectAgentVersion(tenant, definition.getCurrentPublishedVersionId());
                if (version == null) continue;
                var published = legacyNodes(version.getExecutionPlanJson(), "handlerVersion");
                if (!published.isEmpty()) result.add(new Impact(definition.getWorkflowCode(), definition.getWorkflowName(), "PUBLISHED", version.getVersionId(), published));
            }
        }
        return new ImpactView(List.copyOf(result), definitions.size() > 200, Math.min(200, definitions.size()));
    }
    private List<String> legacyNodes(String json, String versionKey) {
        try {
            JsonNode root = JsonMapper.shared().readTree(json); List<String> ids = new ArrayList<>();
            for (JsonNode node : root.path("nodes")) if ("agent".equals(node.path("type").asString())
                    && !"2.0".equals(node.path(versionKey).asString())) ids.add(node.path("id").asString());
            return List.copyOf(ids);
        } catch (RuntimeException invalid) { return List.of("UNREADABLE_DEFINITION"); }
    }
}
