package com.polaris.ai.workflow.node;

import com.polaris.ai.agent.collaboration.*;
import com.polaris.ai.agent.collaboration.AgentCollaborationPorts.*;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.knowledge.KnowledgeRetrievalService;
import com.polaris.ai.mapper.AiAgentMapper;
import com.polaris.ai.modelcenter.runtime.ChatRuntimeService;
import com.polaris.ai.workflow.application.WorkflowResourceOption;
import com.polaris.ai.workflow.config.WorkflowProperties;
import com.polaris.ai.workflow.contract.WorkflowAgentNodeContract;
import com.polaris.ai.workflow.spi.*;
import org.springframework.context.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.*;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** Agent 2.0 仅注册当前协作契约；旧节点不自动改写或回退执行。 */
@Configuration
public class WorkflowAgentNodeConfig {
    public record AgentHandle(AgentBindingService.Snapshot snapshot) { }
    static Long actor(Long tenant, String type, String id, AgentToolCatalog catalog) {
        try {
            if (!"PLATFORM_USER".equals(type) || id == null || !id.matches("[1-9][0-9]{0,18}")) throw new Failure(Code.ACCESS_DENIED);
            Long user = Long.valueOf(id); catalog.requireActor(tenant, user); return user;
        } catch (Failure safe) { throw safe; }
        catch (RuntimeException invalid) { throw new Failure(Code.ACCESS_DENIED); }
    }
    @Bean public WorkflowResourceProvider workflowAgentResourceProvider(AgentBindingService bindings, AgentToolCatalog catalog,
            AiAgentMapper agents, ChatRuntimeService runtime, ToolArgumentValidator arguments, WorkflowProperties properties) {
        var models = new RuntimeAgentModelGateway(runtime); var validator = new AgentPlanValidator(arguments);
        return new WorkflowResourceProvider() {
            @Override public String kind() { return "AGENT"; }
            private AgentBindingService.Snapshot snapshot(WorkflowResourceRequest request) {
                Long user = actor(request.tenantId(), request.principalType(), request.principalId(), catalog);
                var snapshot = bindings.snapshot(request.tenantId(), user, Long.valueOf(request.resourceId()));
                if (!snapshot.bindings().isEmpty() && !properties.isAgentReadOnlyToolsEnabled()) throw new Failure(Code.ACCESS_DENIED);
                snapshot.bindings().forEach(validator::validateBindingDefinition);
                var previous = CallerContextHolder.get();
                try {
                    CallerContextHolder.set(catalog.requireActor(request.tenantId(), user));
                    models.withOverrides(snapshot.modelOverrides()).validate(snapshot.model(), snapshot.bindings().stream().map(binding ->
                            new ToolDefinition(binding.alias(), binding.purpose(), binding.inputSchemaJson())).toList());
                } finally { CallerContextHolder.set(previous); }
                return snapshot;
            }
            @Override public List<String> validate(WorkflowResourceRequest request) {
                try { snapshot(request); return List.of(); }
                catch (RuntimeException denied) { return List.of("协作智能体仅限当前租户管理员；配置、模型能力或工具审批已失效"); }
            }
            @Override public ResolvedWorkflowResource resolve(WorkflowResourceRequest request) {
                var value = snapshot(request);
                return new ResolvedWorkflowResource(kind(), request.resourceKey(), request.resourceId(), 0,
                        attributes(request.resourceId(), value), new AgentHandle(value));
            }
            private Map<String, Object> attributes(String id, AgentBindingService.Snapshot snapshot) {
                ObjectNode frozen = JsonMapper.shared().createObjectNode().put("configVersion", "2.0").put("agentId", id)
                        .put("definitionHash", snapshot.definitionHash());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("snapshot", frozen); result.put("definitionRevision", snapshot.revision().toString());
                result.put("hasTools", !snapshot.bindings().isEmpty()); result.put("toolAccessMode", "MCP_READ_ONLY"); result.put("toolCallLimit", 2);
                result.put("toolBindings", snapshot.bindings().stream().map(binding -> Map.of("alias", binding.alias(),
                        "purpose", binding.purpose(), "toolId", binding.snapshot().toolId().toString(), "revision", String.valueOf(binding.snapshot().revision()))).toList());
                result.put("toolNotice", "仅使用智能体明确绑定的 MCP 只读查询；内部工具类不参与本节点执行");
                return Map.copyOf(result);
            }
            @Override public List<WorkflowResourceOption> listAvailable(WorkflowResourceCatalogRequest request) {
                try { actor(request.tenantId(), request.principalType(), request.principalId(), catalog); }
                catch (RuntimeException denied) { return List.of(); }
                return agents.selectWorkflowResources(request.tenantId()).stream().map(agent -> {
                    String id = agent.getId().toString(); Map<String, Object> attrs = Map.of(); List<String> errors;
                    try {
                        var value = snapshot(new WorkflowResourceRequest(request.tenantId(), request.environment(), kind(), "catalog", id,
                                request.principalType(), request.principalId()));
                        attrs = attributes(id, value); errors = List.of();
                    } catch (RuntimeException denied) { errors = List.of("Agent 已停用、模型能力缺失或协作绑定版本失效"); }
                    return new WorkflowResourceOption(kind(), id, agent.getAgentName(), agent.getRemark(), agent.getStatus(),
                            errors.isEmpty(), errors.isEmpty() ? null : errors.get(0), false, attrs);
                }).toList();
            }
        };
    }

    static AgentBindingService.Snapshot requireSnapshot(JsonNode config, Map<String, ResolvedWorkflowResource> resources,
            Long tenant, Long user, AgentKnowledgeSnapshotService knowledge) {
        WorkflowAgentNodeContract.snapshotVersion(config);
        var agents = resources.values().stream().filter(resource -> "AGENT".equals(resource.kind())).toList();
        var libraries = resources.values().stream().filter(resource -> "KNOWLEDGE_BASE".equals(resource.kind())).toList();
        if (agents.size() != 1 || libraries.size() > 1 || resources.size() != agents.size() + libraries.size()
                || !(agents.get(0).handle() instanceof AgentHandle handle)
                || !config.path("agentId").asString().equals(agents.get(0).resourceId())
                || !config.path("definitionHash").asString().equals(handle.snapshot().definitionHash())
                || config.has("knowledgeId") != (libraries.size() == 1)) throw new Failure(Code.SNAPSHOT_CHANGED);
        if (!libraries.isEmpty()) {
            var library = libraries.get(0);
            if (!config.path("knowledgeId").asString().equals(library.resourceId())
                    || !(library.handle() instanceof WorkflowKnowledgeResourceHandle value)
                    || !tenant.equals(value.knowledge().getTenantId()) || !library.resourceId().equals(value.knowledge().getId().toString()))
                throw new Failure(Code.ACCESS_DENIED);
            knowledge.requireCurrent(tenant, user, library.resourceId(), config.path("knowledgeHash").asString());
        }
        if (handle.snapshot().bindings().isEmpty() && libraries.isEmpty()) throw new Failure(Code.INSUFFICIENT_EVIDENCE);
        if (!handle.snapshot().bindings().isEmpty() && config.path("maxCalls").asInt() < 1) throw new Failure(Code.INVALID_REQUEST);
        return handle.snapshot();
    }
    @Bean public WorkflowNodeSchemaResolver agentWorkflowNodeSchemaResolver(AgentToolCatalog catalog, AgentKnowledgeSnapshotService knowledge) {
        return new WorkflowNodeSchemaResolver() {
            @Override public boolean supports(String type, String version) { return "agent".equals(type) && "2.0".equals(version); }
            @Override public ResolvedNodeSchema resolve(WorkflowNodeSchemaContext context) {
                try {
                    var snapshot = requireSnapshot(context.config(), context.resources(), context.tenantId(),
                            actor(context.tenantId(), context.principalType(), context.principalId(), catalog), knowledge);
                    return new ResolvedNodeSchema(WorkflowAgentNodeContract.inputSchema(!snapshot.bindings().isEmpty()),
                            WorkflowAgentNodeContract.outputSchema(), WorkflowAgentNodeContract.SOURCE,
                            WorkflowAgentNodeContract.snapshotVersion(context.config()), Map.of(), List.of());
                } catch (RuntimeException denied) {
                    return new ResolvedNodeSchema(context.declaredInputSchema(), context.declaredOutputSchema(), WorkflowAgentNodeContract.SOURCE,
                            null, Map.of(), List.of("协作资源未绑定、证据来源为空或快照已变化，请重新确认后发布"));
                }
            }
        };
    }
    @Bean public WorkflowNodeHandler agentWorkflowNodeHandler(AgentToolCatalog actors, AgentKnowledgeSnapshotService knowledge,
            KnowledgeRetrievalService retrieval, ExternalReadToolGateway tools, RunLedger ledger,
            ChatRuntimeService runtime, ToolArgumentValidator arguments) {
        WorkflowNodeDescriptor descriptor = new WorkflowNodeDescriptor("agent", "2.0", "协作智能体", "ai",
                WorkflowAgentNodeContract.configSchema(), WorkflowAgentNodeContract.inputSchema(false), WorkflowAgentNodeContract.outputSchema(),
                WorkflowSideEffect.WRITE, Set.of("AGENT"), Set.of(WorkflowNodeCapability.CANCELLABLE));
        return new WorkflowNodeHandler() {
            @Override public WorkflowNodeDescriptor descriptor() { return descriptor; }
            @Override public WorkflowNodeResult execute(WorkflowNodeContext context) {
                Request request; WorkflowAgentKnowledgeGateway evidence;
                List<com.polaris.ai.modelcenter.runtime.CapabilityParameterOverride> modelOverrides;
                try {
                    context.cancellation().throwIfCancellationRequested();
                    Long user = actor(context.tenantId(), context.principalType(), context.principalId(), actors);
                    var snapshot = requireSnapshot(context.config(), context.resources(), context.tenantId(), user, knowledge);
                    modelOverrides = snapshot.modelOverrides();
                    if (context.attemptNo() != 1 || context.input() == null || !context.input().isObject()
                            || !context.input().path("question").isString()
                            || context.input().has("externalQuery") && !context.input().path("externalQuery").isString()
                            || context.input().propertyNames().stream().anyMatch(key -> !Set.of("question", "externalQuery").contains(key)))
                        throw new Failure(Code.INVALID_REQUEST);
                    request = new Request(new Context(UUID.randomUUID().toString(), context.tenantId(), user, Long.valueOf(context.config().path("agentId").asString()),
                            context.executionId(), context.nodeRunId(), context.attemptNo(), context.workflowCode(), snapshot.definitionHash()), snapshot.model(),
                            snapshot.rolePrompt(), context.input().path("question").asString(), context.input().path("externalQuery").asString(""),
                            context.config().has("knowledgeId"), snapshot.bindings(), context.config().path("maxCalls").asInt(),
                            Duration.ofSeconds(context.config().path("maxWaitSeconds").asInt()));
                    evidence = new WorkflowAgentKnowledgeGateway(context, actors, knowledge, retrieval);
                } catch (RuntimeException denied) {
                    throw new WorkflowNodeExecutionException("AGENT_SNAPSHOT_INVALID", "协作节点配置、主体、输入或资源快照无效；未发起模型或工具请求", true);
                }
                var previous = CallerContextHolder.get();
                try {
                    CallerContextHolder.set(actors.requireActor(request.context().tenantId(), request.context().actorUserId()));
                    var coordinator = new BoundedAgentCoordinator(new RuntimeAgentModelGateway(runtime, modelOverrides), tools, evidence, ledger, new AgentPlanValidator(arguments));
                    Result result = coordinator.execute(request, evidence::checkCurrent);
                    ObjectNode output = JsonMapper.shared().createObjectNode();
                    output.put("text", result.text()); output.put("agentRunId", request.context().runId()); output.put("agentId", request.context().agentId().toString());
                    output.set("knowledgeCitations", JsonMapper.shared().valueToTree(result.knowledgeCitations()));
                    output.set("knowledgeSources", JsonMapper.shared().valueToTree(evidence.sources()));
                    output.set("toolSources", JsonMapper.shared().valueToTree(result.toolSources()));
                    Map<String, Number> usage = usage(request, result);
                    return new WorkflowNodeResult(output, usage, result.toolSources().isEmpty() ? "NONE" : "CONFIRMED");
                } catch (Failure failure) {
                    throw new WorkflowNodeExecutionException("AGENT_" + failure.code().name(), "协作运行停止：" + failure.code().name(),
                            failure.hasDispatchEvidence() && failure.dispatch() == Dispatch.NOT_DISPATCHED);
                } finally { CallerContextHolder.set(previous); }
            }
        };
    }
    static Map<String, Number> usage(Request request, Result result) {
        Map<String, Number> usage = new LinkedHashMap<>(); boolean planning = !request.tools().isEmpty();
        usage.put("modelCalls", planning ? 2 : 1); usage.put("planningCalls", planning ? 1 : 0); usage.put("answerCalls", 1);
        usage.put("toolCalls", result.toolSources().size());
        addUsage(usage, "planning", result.planningUsage()); addUsage(usage, "answer", result.answerUsage());
        Integer[] first = {result.planningUsage().inputTokens(), result.planningUsage().outputTokens(), result.planningUsage().totalTokens()};
        Integer[] second = {result.answerUsage().inputTokens(), result.answerUsage().outputTokens(), result.answerUsage().totalTokens()};
        String[] keys = {"inputTokens", "outputTokens", "totalTokens"};
        for (int i = 0; i < keys.length; i++) if (second[i] != null && (!planning || first[i] != null))
            usage.put(keys[i], second[i].longValue() + (planning ? first[i].longValue() : 0));
        return usage;
    }
    private static void addUsage(Map<String, Number> values, String prefix, Usage usage) {
        if (usage.inputTokens() != null) values.put(prefix + "InputTokens", usage.inputTokens());
        if (usage.outputTokens() != null) values.put(prefix + "OutputTokens", usage.outputTokens());
        if (usage.totalTokens() != null) values.put(prefix + "TotalTokens", usage.totalTokens());
    }
}
