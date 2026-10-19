package com.polaris.ai.agent.collaboration;

import com.polaris.ai.agent.collaboration.AgentCollaborationPorts.RunLedger;
import com.polaris.ai.mapper.AgentRunMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 领取、阶段预算和逻辑调用序号均先落库；不创建 Worker，不自动重放。 */
@Service
public class PersistentAgentRunLedger implements RunLedger, AgentInvocationGuard {
    private final AgentRunMapper runs;
    private final AgentBindingService bindings;
    private final TransactionTemplate tx;
    private final AgentPlanValidator validator;
    private final com.polaris.ai.workflow.config.WorkflowProperties properties;
    private final AgentWorkflowRunVerifier verifier;
    public PersistentAgentRunLedger(AgentRunMapper runs, AgentBindingService bindings,
            AgentCollaborationPorts.ToolArgumentValidator arguments, PlatformTransactionManager transactions,
            com.polaris.ai.workflow.config.WorkflowProperties properties, AgentWorkflowRunVerifier verifier) {
        this.runs = runs; this.bindings = bindings; validator = new AgentPlanValidator(arguments);
        this.properties = properties;
        this.verifier = verifier;
        tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public Lease claim(Request request, String requestHash) {
        if (!AgentPlanValidator.requestHash(request).equals(requestHash)) throw new Failure(Code.INVALID_REQUEST);
        Context context = request.context();
        if (!(context.entry() instanceof AgentEntryIdentity.Workflow)) throw new Failure(Code.ACCESS_DENIED);
        var snapshot = bindings.snapshot(context.tenantId(), context.actorUserId(), context.agentId());
        if (!snapshot.definitionHash().equals(context.definitionHash()) || !snapshot.model().equals(request.model())
                || !snapshot.rolePrompt().equals(request.rolePrompt()) || !snapshot.bindings().equals(request.tools()))
            throw new Failure(Code.SNAPSHOT_CHANGED);
        AgentRunRow row = new AgentRunRow();
        row.setRunId(context.runId()); row.setTenantId(context.tenantId()); row.setActorUserId(context.actorUserId());
        row.setAgentId(context.agentId()); row.setExecutionId(context.executionId()); row.setNodeRunId(context.nodeRunId());
        row.setAttemptNo(context.attemptNo()); row.setWorkflowCode(context.workflowCode()); row.setDefinitionHash(context.definitionHash());
        row.setModelId(request.model().modelId()); row.setModelHash(request.model().definitionHash()); row.setRequestHash(requestHash);
        row.setMaxCalls(request.maxCalls()); row.setStartedTime(new Date());
        row.setStage(request.knowledgeRequired() ? Stage.KNOWLEDGE.name() : request.tools().isEmpty() ? Stage.ANSWER.name() : Stage.PLAN.name());
        row.setDeadline(new Date(System.currentTimeMillis() + request.budget().toMillis()));
        try {
            tx.executeWithoutResult(status -> {
                var execution = runs.execution(row.getTenantId(), row.getExecutionId());
                if (execution == null) throw new Failure(Code.ACCESS_DENIED);
                row.setRunnerId(execution.getRunnerId()); row.setFencingToken(execution.getFencingToken());
                checkFence(row);
                var frozen = verifier.verify(row, execution, runs.node(row.getTenantId(), row.getExecutionId(), row.getNodeRunId()));
                if (request.knowledgeRequired() != frozen.has("knowledgeId")
                        || request.budget().toMillis() > frozen.path("maxWaitSeconds").asLong() * 1000L)
                    throw new Failure(Code.INVALID_REQUEST);
                if (runs.insert(row) != 1) throw new Failure(Code.AUDIT_INCOMPLETE);
            });
        } catch (DuplicateKeyException duplicate) { throw new Failure(Code.DUPLICATE_RUN); }
        catch (Failure safe) { throw safe; }
        catch (RuntimeException unavailable) { throw new Failure(Code.AUDIT_INCOMPLETE); }
        return new PersistentLease(request);
    }

    private void checkFence(AgentRunRow row) {
        if (!properties.isEnabledForTenant(row.getTenantId())) throw new Failure(Code.ACCESS_DENIED);
        var execution = runs.execution(row.getTenantId(), row.getExecutionId());
        var node = runs.node(row.getTenantId(), row.getExecutionId(), row.getNodeRunId());
        if (execution == null || node == null || !"PLATFORM_USER".equals(execution.getPrincipalType())
                || !String.valueOf(row.getActorUserId()).equals(execution.getPrincipalId())
                || !row.getWorkflowCode().equals(execution.getWorkflowCode())) throw new Failure(Code.ACCESS_DENIED);
        if (Boolean.TRUE.equals(execution.getCancelRequested()) || Thread.currentThread().isInterrupted()) throw new Failure(Code.CANCELLED);
        if (!"RUNNING".equals(execution.getStatus()) || !"RUNNING".equals(node.getStatus())
                || row.getRunnerId() == null || !row.getRunnerId().equals(execution.getRunnerId())
                || row.getFencingToken() == null || !row.getFencingToken().equals(execution.getFencingToken())
                || !row.getFencingToken().equals(node.getFencingToken()) || !"2.0".equals(node.getHandlerVersion())
                || execution.getLeaseUntil() == null || !execution.getLeaseUntil().after(new Date())) throw new Failure(Code.CANCELLED);
        if (row.getDeadline() == null || !row.getDeadline().after(new Date())) throw new Failure(Code.DEADLINE_EXCEEDED);
        verifier.verify(row, execution, node);
    }
    private void check(AgentRunRow row) {
        if (row == null || !"RUNNING".equals(row.getStatus())) throw new Failure(Code.DUPLICATE_RUN);
        checkFence(row);
        var snapshot = bindings.snapshot(row.getTenantId(), row.getActorUserId(), row.getAgentId());
        if (!snapshot.bindings().isEmpty() && !properties.isAgentReadOnlyToolsEnabled()) throw new Failure(Code.ACCESS_DENIED);
        if (!row.getDefinitionHash().equals(snapshot.definitionHash()) || !row.getModelId().equals(snapshot.model().modelId())
                || !row.getModelHash().equals(snapshot.model().definitionHash())) throw new Failure(Code.SNAPSHOT_CHANGED);
    }
    private static String argumentsHash(String arguments) {
        return AgentBindingService.digest(AgentPlanValidator.JSON.writeValueAsString(AgentPlanValidator.sorted(
                AgentPlanValidator.parseObject(arguments, 8192, Code.ARGUMENTS_REJECTED))));
    }
    @Override public void checkReserved(ToolIntent intent) {
        var context = intent.context();
        var row = runs.get(context.tenantId(), context.runId());
        check(row);
        if (!row.getActorUserId().equals(context.actorUserId()) || !row.getAgentId().equals(context.agentId())
                || !row.getExecutionId().equals(context.executionId()) || !row.getNodeRunId().equals(context.nodeRunId())
                || !row.getDefinitionHash().equals(context.definitionHash()) || !row.getWorkflowCode().equals(context.workflowCode())
                || !row.getAttemptNo().equals(context.attemptNo()) || row.getAnswerCount() != 0)
            throw new Failure(Code.ACCESS_DENIED);
        var call = runs.calls(context.tenantId(), context.runId()).stream().filter(value -> value.getCallNo() == intent.callNo())
                .findFirst().orElseThrow(() -> new Failure(Code.ACCESS_DENIED));
        var tool = intent.tool();
        if (!"RESERVED".equals(call.getStatus()) || !call.getToolId().equals(tool.toolId())
                || !call.getToolRevision().equals(tool.revision()) || !call.getConnectionRevision().equals(tool.connectionRevision())
                || !call.getCatalogRevision().equals(tool.catalogRevision()) || !call.getStructureHash().equals(tool.structureHash())
                || !call.getArgumentsHash().equals(argumentsHash(intent.argumentsJson()))) throw new Failure(Code.ACCESS_DENIED);
    }

    private final class PersistentLease implements Lease {
        private final Request request;
        private boolean ended;
        PersistentLease(Request request) { this.request = request; }
        private Context context() { return request.context(); }
        private void mutate(Consumer<AgentRunRow> operation) {
            tx.executeWithoutResult(status -> {
                var row = runs.lock(context().tenantId(), context().runId()); check(row);
                operation.accept(row);
                if (runs.update(row) != 1) throw new Failure(Code.AUDIT_INCOMPLETE);
            });
        }
        @Override public void checkActive() { check(runs.get(context().tenantId(), context().runId())); }
        @Override public void reserveModel(Stage stage) {
            mutate(row -> {
                if (stage == Stage.PLAN && row.getPlanCount() == 0 && row.getAnswerCount() == 0 && row.getToolCount() == 0) {
                    row.setPlanCount(1);
                } else if (stage == Stage.ANSWER && row.getAnswerCount() == 0
                        && (request.tools().isEmpty() || Boolean.TRUE.equals(row.getPlanUsageRecorded()))
                        && runs.calls(row.getTenantId(), row.getRunId()).stream().allMatch(call -> "SUCCESS".equals(call.getStatus()))) {
                    row.setAnswerCount(1);
                } else throw new Failure(Code.BUDGET_EXCEEDED);
                row.setStage(stage.name());
            });
        }
        @Override public void recordModelUsage(Stage stage, Usage usage) {
            mutate(row -> {
                if (stage == Stage.PLAN && row.getPlanCount() == 1 && !Boolean.TRUE.equals(row.getPlanUsageRecorded()) && row.getAnswerCount() == 0) {
                    row.setPlanUsageRecorded(true); row.setPlanInputTokens(usage.inputTokens() == null ? null : usage.inputTokens().longValue()); row.setPlanOutputTokens(usage.outputTokens() == null ? null : usage.outputTokens().longValue()); row.setPlanTotalTokens(usage.totalTokens() == null ? null : usage.totalTokens().longValue());
                } else if (stage == Stage.ANSWER && row.getAnswerCount() == 1 && !Boolean.TRUE.equals(row.getAnswerUsageRecorded())) {
                    row.setAnswerUsageRecorded(true); row.setAnswerInputTokens(usage.inputTokens() == null ? null : usage.inputTokens().longValue()); row.setAnswerOutputTokens(usage.outputTokens() == null ? null : usage.outputTokens().longValue()); row.setAnswerTotalTokens(usage.totalTokens() == null ? null : usage.totalTokens().longValue());
                } else throw new Failure(Code.AUDIT_INCOMPLETE);
            });
        }
        @Override public void reserveTool(ToolIntent intent) {
            if (!context().equals(intent.context())) throw new Failure(Code.ACCESS_DENIED);
            var bound = request.tools().stream().filter(tool -> tool.snapshot().equals(intent.tool())).findFirst()
                    .orElseThrow(() -> new Failure(Code.ACCESS_DENIED));
            var checked = validator.validate(request, new Plan(List.of(new Proposal("reserved", bound.alias(), intent.argumentsJson())), Usage.unknown()));
            if (checked.size() != 1) throw new Failure(Code.ARGUMENTS_REJECTED);
            mutate(row -> {
                var calls = runs.calls(row.getTenantId(), row.getRunId());
                String argsHash = argumentsHash(intent.argumentsJson());
                if (row.getPlanCount() != 1 || !Boolean.TRUE.equals(row.getPlanUsageRecorded()) || row.getAnswerCount() != 0
                        || intent.callNo() != row.getToolCount() + 1 || intent.callNo() > row.getMaxCalls()
                        || calls.stream().anyMatch(call -> !"SUCCESS".equals(call.getStatus())
                            || call.getToolId().equals(intent.tool().toolId()) && call.getArgumentsHash().equals(argsHash)))
                    throw new Failure(Code.BUDGET_EXCEEDED);
                AgentRunCallRow call = new AgentRunCallRow();
                call.setTenantId(row.getTenantId()); call.setRunId(row.getRunId()); call.setCallNo(intent.callNo());
                call.setToolId(intent.tool().toolId()); call.setToolRevision(intent.tool().revision());
                call.setConnectionRevision(intent.tool().connectionRevision()); call.setCatalogRevision(intent.tool().catalogRevision());
                call.setStructureHash(intent.tool().structureHash()); call.setArgumentsHash(argsHash);
                if (runs.insertCall(call) != 1) throw new Failure(Code.AUDIT_INCOMPLETE);
                row.setToolCount(intent.callNo()); row.setStage(Stage.TOOL.name());
            });
        }
        @Override public void recordTool(int callNo, String invocationId, boolean succeeded, Dispatch dispatch) {
            mutate(row -> {
                var call = runs.calls(row.getTenantId(), row.getRunId()).stream().filter(value -> value.getCallNo() == callNo)
                        .findFirst().orElseThrow(() -> new Failure(Code.AUDIT_INCOMPLETE));
                if (invocationId == null || !invocationId.matches("[A-Za-z0-9_.:-]{1,64}") || dispatch == null) throw new Failure(Code.AUDIT_INCOMPLETE);
                call.setInvocationId(invocationId); call.setStatus(succeeded ? "SUCCESS" : "FAILED"); call.setDispatch(dispatch.name());
                if (runs.finishCall(call) != 1) throw new Failure(Code.AUDIT_INCOMPLETE);
                row.setDispatch(dispatch.name());
            });
        }
        @Override public void complete(List<ToolSource> sources) {
            mutate(row -> {
                var calls = runs.calls(row.getTenantId(), row.getRunId());
                if (!Boolean.TRUE.equals(row.getAnswerUsageRecorded()) || sources.size() != calls.size()
                        || calls.stream().anyMatch(call -> !"SUCCESS".equals(call.getStatus()) || sources.stream().noneMatch(
                            source -> source.callNo() == call.getCallNo() && source.invocationId().equals(call.getInvocationId()))))
                    throw new Failure(Code.AUDIT_INCOMPLETE);
                row.setStatus("SUCCEEDED"); row.setFinishedTime(new Date());
            });
            ended = true;
        }
        @Override public void fail(Code code, Dispatch dispatch) {
            boolean interrupted = Thread.interrupted();
            try {
                tx.executeWithoutResult(status -> {
                    var row = runs.lock(context().tenantId(), context().runId());
                    if (row != null && "RUNNING".equals(row.getStatus())) {
                        row.setStatus("FAILED"); row.setErrorCode(code.name()); row.setDispatch(dispatch.name()); row.setFinishedTime(new Date());
                        if (runs.update(row) != 1) throw new Failure(Code.AUDIT_INCOMPLETE);
                    }
                });
                ended = true;
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        @Override public void close() { if (!ended) fail(Code.RESULT_UNKNOWN, Dispatch.UNKNOWN); }
    }
}
