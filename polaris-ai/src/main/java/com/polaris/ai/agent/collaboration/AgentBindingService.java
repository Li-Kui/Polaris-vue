package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.mapper.AgentBindingMapper;
import com.polaris.ai.mapper.AiAgentMapper;
import com.polaris.ai.modelcenter.runtime.ModelDefinitionResolver;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 配置修订与绑定替换在同一短事务中提交；预检只读本地，不调用供应商。 */
@Service
public class AgentBindingService {
    private final AiAgentMapper agents;
    private final AgentBindingMapper bindings;
    private final AgentToolCatalog catalog;
    private final ModelDefinitionResolver models;
    private final AgentPlanValidator validator;
    private final RuntimeAgentModelGateway gateway;
    private final TransactionTemplate tx;
    public AgentBindingService(AiAgentMapper agents, AgentBindingMapper bindings, AgentToolCatalog catalog,
            ModelDefinitionResolver models, AgentCollaborationPorts.ToolArgumentValidator arguments,
            com.polaris.ai.modelcenter.runtime.ChatRuntimeService runtime, PlatformTransactionManager transactions) {
        this.agents = agents; this.bindings = bindings; this.catalog = catalog; this.models = models;
        validator = new AgentPlanValidator(arguments); gateway = new RuntimeAgentModelGateway(runtime);
        tx = new TransactionTemplate(transactions);
    }
    public record BindingInput(Long toolId, String purpose, String queryArgument, String fixedArgumentsJson) {
        @Override public String toString() { return "AgentBindingInput[toolId=" + toolId + "]"; }
    }
    public record Save(Long revision, List<BindingInput> bindings) { }
    public record Snapshot(Long revision, String definitionHash, ModelSelection model, String rolePrompt,
            List<ToolBinding> bindings, List<com.polaris.ai.modelcenter.runtime.CapabilityParameterOverride> modelOverrides) {
        @Override public String toString() { return "AgentSnapshot[revision=" + revision + "]"; }
    }
    public record BindingView(ToolBinding binding, boolean available, String reason) { }
    public record View(Long revision, List<BindingView> bindings, List<AgentToolCatalog.CatalogTool> tools) { }

    public View view(Long agentId) {
        var actor = managementActor();
        Long tenant = Long.valueOf(actor.getTenantId());
        AiAgent agent = requireAgent(agents.selectWorkflowResource(tenant, agentId), tenant);
        List<BindingView> rows = bindings.list(tenant, agentId).stream().map(row -> {
            ToolBinding bound = row.binding();
            try { requireCurrent(tenant, actor.getUserId(), bound); return new BindingView(bound, true, null); }
            catch (Failure changed) { return new BindingView(bound, false, changed.code().name()); }
        }).toList();
        return new View(agent.getDefinitionRevision(), rows, catalog.list(tenant, actor.getUserId()));
    }

    public View save(Long agentId, Save request) {
        var actor = managementActor();
        Long tenant = Long.valueOf(actor.getTenantId());
        if (request == null || request.revision() == null || request.bindings() == null
                || request.bindings().size() > 6) throw new Failure(Code.INVALID_REQUEST);
        tx.executeWithoutResult(status -> {
            AiAgent agent = requireAgent(agents.lockScoped(tenant, agentId), tenant);
            if (!request.revision().equals(agent.getDefinitionRevision()))
                throw new ServiceException("智能体或工具绑定已变更，请刷新后重新配置", 409);
            Set<Long> ids = new HashSet<>();
            List<AgentBindingRow> rows = new ArrayList<>();
            for (BindingInput input : request.bindings()) {
                if (input == null || input.toolId() == null || !ids.add(input.toolId())) throw new Failure(Code.INVALID_REQUEST);
                var tool = catalog.resolve(tenant, actor.getUserId(), input.toolId());
                ToolBinding binding = new ToolBinding("tool_" + (rows.size() + 1), tool.snapshot(), input.purpose(),
                        tool.inputSchemaJson(), input.queryArgument(), AgentPlanValidator.JSON.writeValueAsString(
                            AgentPlanValidator.sorted(AgentPlanValidator.parseObject(input.fixedArgumentsJson(), 8192, Code.ARGUMENTS_REJECTED))));
                validator.validateBindingDefinition(binding);
                AgentBindingRow row = new AgentBindingRow();
                row.setTenantId(tenant); row.setAgentId(agentId); row.setToolId(input.toolId());
                row.setAlias(binding.alias()); row.setPurpose(binding.purpose()); row.setQueryArgument(binding.queryArgument());
                row.setFixedArgumentsJson(binding.fixedArgumentsJson()); row.setInputSchemaJson(binding.inputSchemaJson());
                row.setToolRevision(tool.snapshot().revision()); row.setConnectionRevision(tool.snapshot().connectionRevision());
                row.setCatalogRevision(tool.snapshot().catalogRevision()); row.setStructureHash(tool.snapshot().structureHash());
                rows.add(row);
            }
            if (agents.bumpRevision(tenant, agentId, request.revision()) != 1) throw new Failure(Code.SNAPSHOT_CHANGED);
            bindings.delete(tenant, agentId);
            rows.forEach(row -> { if (bindings.insert(row) != 1) throw new Failure(Code.AUDIT_INCOMPLETE); });
        });
        return view(agentId);
    }

    public Snapshot snapshot(Long tenant, Long user, Long agentId) {
        var actor = catalog.requireActor(tenant, user);
        var previous = CallerContextHolder.get();
        try {
            CallerContextHolder.set(actor);
            AiAgent agent = requireAgent(agents.selectWorkflowResource(tenant, agentId), tenant);
            if (!"1".equals(agent.getStatus())) throw new Failure(Code.ACCESS_DENIED);
            if (agent.getModelConfigId() == null) throw new Failure(Code.INVALID_REQUEST);
            var model = models.resolve(agent.getModelConfigId(), "CHAT_COMPLETION");
            List<ToolBinding> bound = bindings.list(tenant, agentId).stream().map(AgentBindingRow::binding).toList();
            bound.forEach(binding -> requireCurrent(tenant, user, binding));
            String hash = digest(AgentPlanValidator.JSON.writeValueAsString(AgentPlanValidator.sorted(
                    AgentPlanValidator.JSON.valueToTree(List.of(agentId, agent.getDefinitionRevision(),
                            model.runtimeDefinitionHash(), bound, Objects.toString(agent.getSystemPrompt(), ""),
                            com.polaris.ai.modelcenter.runtime.AgentRuntimeOverrides.from(agent))))));
            return new Snapshot(agent.getDefinitionRevision(), hash,
                    new ModelSelection(agent.getModelConfigId(), model.runtimeDefinitionHash()),
                    agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt(), bound,
                    com.polaris.ai.modelcenter.runtime.AgentRuntimeOverrides.from(agent));
        } finally { CallerContextHolder.set(previous); }
    }

    public record Preflight(Long revision, String externalQuery) {
        @Override public String toString() { return "AgentPreflight[revision=" + revision + "]"; }
    }
    public record PreflightResult(Long revision, String definitionHash, int toolCount, String status) { }
    public PreflightResult preflight(Long id, Preflight input) {
        var actor = managementActor(); Long tenant = Long.valueOf(actor.getTenantId());
        Snapshot snapshot = snapshot(tenant, actor.getUserId(), id);
        if (input == null || !snapshot.revision().equals(input.revision())) throw new Failure(Code.SNAPSHOT_CHANGED);
        Request request = new Request(new Context("preflight", tenant, actor.getUserId(), id,
                "preflight", "preflight", 1, "preflight", snapshot.definitionHash()), snapshot.model(),
                snapshot.rolePrompt(), "本地预检", input.externalQuery(), false, snapshot.bindings(), 2, Duration.ofSeconds(10));
        gateway.withOverrides(snapshot.modelOverrides()).validate(snapshot.model(), validator.prepare(request));
        return new PreflightResult(snapshot.revision(), snapshot.definitionHash(), snapshot.bindings().size(), "LOCAL_READY");
    }

    private void requireCurrent(Long tenant, Long user, ToolBinding binding) {
        var current = catalog.resolve(tenant, user, binding.snapshot().toolId());
        if (!current.snapshot().equals(binding.snapshot()) || !AgentPlanValidator.JSON.readTree(current.inputSchemaJson())
                .equals(AgentPlanValidator.JSON.readTree(binding.inputSchemaJson()))) throw new Failure(Code.SNAPSHOT_CHANGED);
    }
    private AiAgent requireAgent(AiAgent agent, Long tenant) {
        if (agent == null || !tenant.equals(agent.getTenantId())) throw new Failure(Code.ACCESS_DENIED);
        return agent;
    }
    private com.polaris.ai.core.context.CallerContext managementActor() {
        var actor = CallerContextHolder.require();
        try {
            if (!actor.isPlatformMode() || !actor.isTenantAdmin()) throw new Failure(Code.ACCESS_DENIED);
            return catalog.requireActor(Long.valueOf(actor.getTenantId()), actor.getUserId());
        } catch (Failure denied) { throw denied; }
        catch (RuntimeException denied) { throw new Failure(Code.ACCESS_DENIED); }
    }
    static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(); }
    }
}
