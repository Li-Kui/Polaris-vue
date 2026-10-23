package com.polaris.ai.agent.collaboration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.polaris.ai.agent.collaboration.AgentCollaborationPorts.*;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 共用的有界协调内核；入口适配器注入持久许可与完成端口，不使用自动工具循环。 */
public final class BoundedAgentCoordinator {
    private final ModelGateway models;
    private final ExternalReadToolGateway tools;
    private final KnowledgeGateway knowledge;
    private final RunLedger ledger;
    private final AgentPlanValidator plans;
    public BoundedAgentCoordinator(ModelGateway models, ExternalReadToolGateway tools,
            KnowledgeGateway knowledge, RunLedger ledger, AgentPlanValidator plans) {
        this.models = Objects.requireNonNull(models); this.tools = Objects.requireNonNull(tools);
        this.knowledge = Objects.requireNonNull(knowledge); this.ledger = Objects.requireNonNull(ledger);
        this.plans = Objects.requireNonNull(plans);
    }

    public Result execute(Request request) {
        return execute(request, () -> { });
    }

    /** 节点额外加入取消与知识来源探针，持久化授权仍强制由 Ledger 提供。 */
    public Result execute(Request request, Runnable additionalCheck) {
        Objects.requireNonNull(request);
        // claim 必须先鉴权；未持久化的默认实现不存在。
        try (RunLedger.Lease lease = ledger.claim(request, AgentPlanValidator.requestHash(request));
             AgentCallControl control = new AgentCallControl(request.budget(), () -> {lease.checkActive(); additionalCheck.run();})) {
            Dispatch dispatch = Dispatch.NOT_DISPATCHED;
            try {
                control.check();
                tools.validateBindings(request.context(), request.tools());
                List<ToolDefinition> definitions = plans.prepare(request);
                models.validate(request.model(), definitions);
                control.check();
                List<KnowledgeEvidence> evidence = request.knowledgeRequired()
                        ? List.copyOf(knowledge.retrieve(request, control)) : List.of();
                control.check();
                if (request.knowledgeRequired() && evidence.isEmpty()) throw new Failure(Code.INSUFFICIENT_EVIDENCE);
                if (evidence.stream().map(KnowledgeEvidence::citationId).distinct().count() != evidence.size()
                        || evidence.stream().mapToLong(item -> item.content().length()).sum() > 16000) {
                    throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);
                }
                Plan plan = new Plan(List.of(), Usage.unknown());
                if (!definitions.isEmpty()) {
                    control.check();
                    lease.reserveModel(Stage.PLAN);
                    plan = models.plan(new PlanningInput(request.model(), request.externalQuery(), definitions), control);
                    control.check();
                    if (plan == null) throw new Failure(Code.MODEL_OUTPUT_INVALID);
                    lease.recordModelUsage(Stage.PLAN, plan.usage());
                }
                List<ToolIntent> intents = plans.validate(request, plan);
                List<ToolEvidence> results = new ArrayList<>();
                for (ToolIntent intent : intents) {
                    control.check();
                    tools.validateBindings(request.context(), request.tools());
                    lease.reserveTool(intent);
                    // Gateway 进入后即保守未知，只有明确派发证据才能收紧分类。
                    Dispatch previous = dispatch;
                    dispatch = Dispatch.UNKNOWN;
                    ToolResult result;
                    try { result = tools.invoke(intent, control); }
                    catch (Failure failure) {
                        dispatch = previous == Dispatch.PREPARED ? previous
                                : failure.hasDispatchEvidence() ? failure.dispatch() : Dispatch.UNKNOWN;
                        throw failure;
                    }
                    if (result == null) throw new Failure(Code.RESULT_UNKNOWN, Dispatch.UNKNOWN);
                    dispatch = previous == Dispatch.PREPARED ? previous : result.dispatch();
                    lease.recordTool(intent.callNo(), result.invocationId(), result.succeeded(), result.dispatch());
                    control.check();
                    if (!result.auditRecorded()) throw new Failure(Code.AUDIT_INCOMPLETE, dispatch);
                    if (result.dispatch() == Dispatch.UNKNOWN) throw new Failure(Code.RESULT_UNKNOWN, dispatch);
                    if (!result.succeeded()) throw new Failure(Code.TOOL_FAILED, dispatch);
                    if (result.dispatch() != Dispatch.PREPARED || result.content() == null) throw new Failure(Code.RESULT_UNKNOWN, dispatch);
                    String content = result.content();
                    int existing = results.stream().mapToInt(item -> item.content().length()).sum();
                    int knowledgeChars = evidence.stream().mapToInt(item -> item.content().length()).sum();
                    int available = Math.min(8000, Math.min(12000 - existing, 16000 - existing - knowledgeChars));
                    if (available < 1) throw new Failure(Code.BUDGET_EXCEEDED, dispatch);
                    int limit = Math.min(content.length(), available);
                    if (limit < content.length() && limit > 0 && Character.isHighSurrogate(content.charAt(limit - 1))) limit--;
                    if (limit == 0 && !content.isEmpty()) throw new Failure(Code.BUDGET_EXCEEDED, dispatch);
                    results.add(new ToolEvidence("T" + intent.callNo(), result.invocationId(), intent.callNo(),
                            content.substring(0, limit), result.truncated() || limit < content.length()));
                }
                if (evidence.isEmpty() && results.isEmpty() && request.evidencePolicy()!=EvidencePolicy.CONTINUE_HISTORY)
                    throw new Failure(Code.INSUFFICIENT_EVIDENCE, dispatch);
                control.check();
                tools.validateBindings(request.context(), request.tools());
                knowledge.recheck(request.context(), evidence);
                lease.reserveModel(Stage.ANSWER);
                Answer answer = models.answer(new AnswerInput(request.model(), request.rolePrompt(), request.question(), evidence, results,
                        request.history(),request.evidencePolicy()), control);
                control.check();
                tools.validateBindings(request.context(), request.tools());
                knowledge.recheck(request.context(), evidence);
                if (answer == null) throw new Failure(Code.MODEL_OUTPUT_INVALID, dispatch);
                lease.recordModelUsage(Stage.ANSWER, answer.usage());
                List<ToolSource> sources = results.stream().map(item -> new ToolSource(item.citationId(),
                        item.invocationId(), item.callNo(), item.truncated())).toList();
                Result completed = new Result(answer.text(), evidence.stream().map(KnowledgeEvidence::citationId).toList(), sources,
                        plan.usage(), answer.usage(),answer.historyMessageIds());
                lease.complete(completed);
                return completed;
            } catch (Failure failure) {
                Dispatch effective = dispatch == Dispatch.PREPARED || dispatch == Dispatch.UNKNOWN ? dispatch : failure.dispatch();
                lease.fail(failure.code(), effective);
                throw new Failure(failure.code(), effective);
            } catch (RuntimeException failure) {
                lease.fail(Code.MODEL_FAILED, dispatch);
                throw new Failure(Code.MODEL_FAILED, dispatch);
            }
        } catch (Failure failure) { throw failure; }
        catch (RuntimeException failure) {
            // 领取、记账或关闭许可失败也不传播数据库／提供器的原始异常正文。
            throw new Failure(Code.AUDIT_INCOMPLETE, Dispatch.UNKNOWN);
        }
    }
}
