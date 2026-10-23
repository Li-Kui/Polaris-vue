package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.mapper.AgentChatMapper;
import com.polaris.ai.modelcenter.runtime.ChatRuntimeService;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;

/** 单轮确认、领取与持久取消；执行由受控适配器承接，没有后台重跑器。 */
@Service
public class AgentChatPreparationService {
    private final AgentChatMapper chats;
    private final AgentBindingService agents;
    private final AgentKnowledgeSnapshotService knowledge;
    private final AgentToolCatalog actors;
    private final AgentChatHistoryService history;
    private final RuntimeAgentModelGateway models;
    private final AgentPlanValidator validator;
    private final TransactionTemplate tx;
    public AgentChatPreparationService(AgentChatMapper chats, AgentBindingService agents, AgentKnowledgeSnapshotService knowledge,
            AgentToolCatalog actors, AgentChatHistoryService history, ChatRuntimeService runtime,
            AgentCollaborationPorts.ToolArgumentValidator arguments, PlatformTransactionManager transactions) {
        this.chats = chats; this.agents = agents; this.knowledge = knowledge; this.actors = actors; this.history = history;
        models = new RuntimeAgentModelGateway(runtime); validator = new AgentPlanValidator(arguments);
        tx = new TransactionTemplate(transactions);
    }
    private record Actor(Long tenant, Long user) { }
    private Actor actor() {
        try {
            var caller = CallerContextHolder.require();
            if (!caller.isPlatformMode() || !caller.isTenantAdmin()) throw denied();
            Long tenant = Long.valueOf(caller.getTenantId()); positive(tenant); positive(caller.getUserId());
            var current = actors.requireActor(tenant, caller.getUserId());
            if (!current.isPlatformMode() || !current.isTenantAdmin() || !Objects.equals(current.getUserId(), caller.getUserId())
                    || !tenant.toString().equals(current.getTenantId())) throw denied();
            return new Actor(tenant, caller.getUserId());
        } catch (RuntimeException rejected) { throw denied(); }
    }
    private Scope scope(Actor actor, Long conversation) { return new Scope(actor.tenant(), actor.user(), conversation); }
    private AgentChatConversationRow conversation(Scope scope) {
        var row = chats.lockConversation(scope); if (row == null) throw denied(); return row;
    }
    private static Date plus(Date time, int seconds) { return new Date(time.getTime() + seconds * 1000L); }
    private static String inputHash(Input input) { return digest(List.of(POLICY, input)); }
    private static String json(Object value) { return AgentPlanValidator.JSON.writeValueAsString(value); }
    private static <T> T decode(String value, Class<T> type) {
        if (value == null || value.length() > 200000) throw conflict();
        try { return Objects.requireNonNull(AgentPlanValidator.JSON.readValue(value, type)); }
        catch (RuntimeException invalid) { throw conflict(); }
    }
    private void transition(AgentChatRequestRow row, String status, String token) {
        if (chats.transition(row, status, token) != 1) throw conflict();
    }

    private record Preparation(ResourceSnapshot resources, History history, String contextKey, long start) { }
    private Preparation build(Scope scope, AgentChatConversationRow conversation, Input input) {
        var agent = agents.snapshot(scope.tenantId(), scope.actorUserId(), input.agentId());
        if (agent.rolePrompt().length() > 8000) throw invalid();
        var selected = input.allowExternalTools() ? agent.bindings() : List.<BoundedAgentContracts.ToolBinding>of();
        if (input.allowExternalTools() && selected.isEmpty()) throw new ServiceException("该智能体未绑定可用的协作工具", 409);
        if (input.answerMode() == AnswerMode.QUERY_EVIDENCE && input.knowledgeBaseId() == null && selected.isEmpty())
            throw new ServiceException("请选择知识库、开启本轮查询，或选择基于前文回答", 400);
        String knowledgeHash = input.answerMode() == AnswerMode.QUERY_EVIDENCE && input.knowledgeBaseId() != null
                ? knowledge.resolve(scope.tenantId(), scope.actorUserId(), input.knowledgeBaseId()).definitionHash() : null;
        models.withOverrides(agent.modelOverrides()).validate(agent.model(), validator.prepareDefinitions(input.externalQuery(), selected));
        List<ToolTarget> targets = selected.stream().map(binding -> new ToolTarget(binding.snapshot().toolId(), binding.alias(),
                actors.resolve(scope.tenantId(), scope.actorUserId(), binding.snapshot().toolId()).label(), binding.purpose(), binding.fixedArgumentsJson())).toList();
        var resources = new ResourceSnapshot(POLICY, agent.revision(), agent.definitionHash(), agent.model().modelId(),
                agent.model().definitionHash(), knowledgeHash, selected.stream().map(BoundedAgentContracts.ToolBinding::snapshot).toList(), targets,
                new InputProof(digest(input.question()),digest(input.externalQuery())));
        String contextKey = digest(Arrays.asList(input.agentId(), agent.model().modelId(), agent.model().definitionHash(), input.knowledgeBaseId()));
        long start = conversation.getCollaborationContextStartId() == null ? 0 : conversation.getCollaborationContextStartId();
        // 同一模型/Agent/知识选择连续承接；换目标默认新段，显式继承才允许收窄到旧段。
        if (!contextKey.equals(conversation.getCollaborationContextKey()) && !input.inheritPreviousContext()) {
            start = chats.history(scope).stream().mapToLong(AgentChatHistoryRow::getId).max().orElse(0) + 1;
        } else if (input.inheritPreviousContext()) { start = 0; }
        if (input.contextStartMessageId() != null) {
            if (chats.ownsMessage(scope, input.contextStartMessageId()) != 1 || input.contextStartMessageId() < start) throw invalid();
            start = input.contextStartMessageId();
        }
        var found = history.build(scope, contextKey, start,
                input.inheritPreviousContext() || contextKey.equals(conversation.getCollaborationContextKey()));
        var inherited=history.inherited(found.snapshot());
        int reserve=input.answerMode()==AnswerMode.QUERY_EVIDENCE?(input.knowledgeBaseId()==null?0:5)+(selected.isEmpty()?0:2):0;
        if(inherited.knowledge().size()+inherited.tools().size()+inherited.history().size()>MAX_DEPENDENCIES-reserve)
            throw new ServiceException("前文来源较多，请开始新话题或缩小承接范围",409);
        if (input.answerMode() == AnswerMode.CONTINUE_HISTORY && found.messages().isEmpty())
            throw new ServiceException("当前没有可用前文，请先查询资料或明确承接已有对话", 409);
        return new Preparation(resources, found, contextKey, start);
    }

    public PreparedView prepare(Input input) {
        if (input == null) throw invalid(); var actor = actor(); var scope = scope(actor, input.conversationId());
        return tx.execute(status -> {
            var conversation = conversation(scope);
            var existing = chats.byKey(scope, input.requestKey());
            if (existing != null) {
                if (!inputHash(input).equals(existing.getInputHash())) throw conflict();
                var row = chats.lock(scope, existing.getRequestId()); settle(scope, row); return view(row);
            }
            var prepared = build(scope, conversation, input); Date now = chats.now();
            AgentChatRequestRow row = new AgentChatRequestRow();
            row.setRequestId(UUID.randomUUID().toString()); row.setTenantId(scope.tenantId()); row.setActorUserId(scope.actorUserId());
            row.setConversationId(scope.conversationId()); row.setRequestKey(input.requestKey()); row.setInputHash(inputHash(input));
            row.setSnapshotJson(json(prepared.resources())); row.setSnapshotHash(digest(prepared.resources()));
            row.setHistoryJson(json(prepared.history().snapshot())); row.setHistoryHash(digest(prepared.history().snapshot()));
            row.setHistoryWatermark(prepared.history().snapshot().watermark()); row.setContextKey(prepared.contextKey());
            row.setContextStartMessageId(prepared.start()); row.setAnswerMode(input.answerMode().name()); row.setAgentId(input.agentId());
            row.setModelId(prepared.resources().modelId()); row.setKnowledgeBaseId(input.knowledgeBaseId());
            row.setMaxCalls(prepared.resources().tools().isEmpty() ? 0 : 2); row.setMaxWaitSeconds(MAX_WAIT_SECONDS);
            row.setCreatedTime(now); row.setExpiresAt(plus(now, 300)); row.setStatus(Status.PREPARED.name());
            if (chats.insert(row) != 1) throw conflict();
            return view(row);
        });
    }

    private AgentChatRequestRow owned(Actor actor, String requestId) {
        id(requestId);
        var row = chats.get(actor.tenant(), actor.user(), requestId); if (row == null) throw denied(); return row;
    }
    public PreparedView get(String requestId) {
        var actor = actor(); var initial = owned(actor, requestId); var scope = scope(actor, initial.getConversationId());
        return tx.execute(status -> {
            conversation(scope); var row = chats.lock(scope, requestId); if (row == null) throw denied();
            settle(scope, row); return view(row);
        });
    }
    public PreparedView forRun(String runId) {id(runId);var actor=actor();var row=chats.forRun(actor.tenant(),actor.user(),runId);if(row==null)throw denied();return get(row.getRequestId());}
    public PreparedView cancel(String requestId) {
        var actor = actor(); var initial = owned(actor, requestId); var scope = scope(actor, initial.getConversationId());
        return tx.execute(status -> {
            conversation(scope); var row = chats.lock(scope, requestId); if (row == null) throw denied(); settle(scope, row);
            if (Status.PREPARED.name().equals(row.getStatus())) {
                row.setCancelRequestedAt(chats.now()); row.setFinishedTime(row.getCancelRequestedAt()); row.setStatus(Status.CANCELLED.name());
                row.setErrorCode("CANCELLED"); transition(row, Status.PREPARED.name(), null);
            } else if (Status.RUNNING.name().equals(row.getStatus()) && row.getCancelRequestedAt() == null) {
                row.setCancelRequestedAt(chats.now()); transition(row, Status.RUNNING.name(), row.getOwnerToken());
                chats.cancelGeneration(scope, row.getRequestId());
            }
            return view(row);
        });
    }

    /** 先领取到 INPUT 阶段；审核允许后 admitUser 才保存正文并开放后续阶段，重复领取不发第二个 permit。 */
    public Permit claim(Input input, String requestId) {
        if (input == null) throw invalid(); var actor = actor(); var initial = owned(actor, requestId);
        var scope = scope(actor, input.conversationId());
        if (!Objects.equals(initial.getConversationId(), scope.conversationId())) throw denied();
        return tx.execute(status -> {
            var conversation = conversation(scope); var row = chats.lock(scope, requestId); if (row == null) throw denied();
            settle(scope, row);
            if (!inputHash(input).equals(row.getInputHash()) || !Status.PREPARED.name().equals(row.getStatus())) throw conflict();
            var prepared = build(scope, conversation, input);
            if (!digest(prepared.resources()).equals(row.getSnapshotHash()) || !digest(prepared.history().snapshot()).equals(row.getHistoryHash())
                    || !prepared.contextKey().equals(row.getContextKey()) || prepared.start() != row.getContextStartMessageId()) throw conflict();
            Date now = chats.now(); if (!row.getExpiresAt().after(now)) throw conflict();
            var occupied = chats.active(scope);
            if (occupied != null) {
                if (occupied.getLeaseUntil().after(now) && occupied.getDeadline().after(now))
                    throw new ServiceException("当前会话正在生成，请等待完成或停止后再发送", 409);
                if ("AGENT_CHAT".equals(occupied.getEntryType())) {
                    var old = chats.lock(scope, occupied.getGenerationId());
                    if (old != null) settle(scope, old);
                }
                chats.release(scope, occupied.getGenerationId(), occupied.getOwnerToken());
            }
            row.setRunId(UUID.randomUUID().toString()); row.setOwnerToken(UUID.randomUUID().toString());
            row.setStatus(Status.RUNNING.name()); row.setStartedTime(now); row.setDeadline(plus(now, row.getMaxWaitSeconds()));
            row.setLeaseUntil(plus(now, LEASE_SECONDS));
            var generation = new ChatGenerationRow(); generation.setTenantId(scope.tenantId()); generation.setActorUserId(scope.actorUserId());
            generation.setConversationId(scope.conversationId()); generation.setEntryType("AGENT_CHAT"); generation.setGenerationId(row.getRequestId());
            generation.setOwnerToken(row.getOwnerToken()); generation.setLeaseUntil(row.getLeaseUntil()); generation.setDeadline(row.getDeadline());
            if (chats.occupy(generation) != 1 || chats.setContext(scope, row.getContextKey(), row.getContextStartMessageId()) != 1) throw conflict();
            if (chats.insertRun(row, prepared.resources().agentHash(), prepared.resources().modelHash(),"INPUT") != 1) throw conflict();
            transition(row, Status.PREPARED.name(), null);
            return new Permit(scope, new AgentEntryIdentity.Chat(scope.conversationId(), requestId), row.getRunId(), row.getOwnerToken(), row.getDeadline());
        });
    }

    public PreparedView submission(Input input,String requestId) {
        if(input==null)throw invalid();var actor=actor();var initial=owned(actor,requestId);
        if(!Objects.equals(input.conversationId(),initial.getConversationId()) || !inputHash(input).equals(initial.getInputHash()))throw conflict();
        return get(requestId);
    }
    /** 先领取再审核，审核完成后才保存正文；重复请求不会重复发起审核或模型调用。 */
    public Long admitUser(Permit permit,Input input,com.polaris.ai.safety.dto.ModerationResult approval) {
        if(approval==null || !approval.isAllowed())throw denied();
        return tx.execute(status-> {
            checkActive(permit);var row=chats.lock(permit.scope(),permit.identity().requestId());
            if(!inputHash(input).equals(row.getInputHash()) || row.getUserMessageId()!=null)throw conflict();
            var message=new AgentChatHistoryRow();message.setConversationId(permit.scope().conversationId());message.setContent(input.question());
            message.setAgentRunId(permit.runId());message.setChatContextKey(row.getContextKey());
            if(chats.insertUserMessage(message)!=1 || message.getId()==null || chats.approveUser(permit.scope(),permit.runId(),approval.dictionaryVersion())!=1)throw conflict();
            row.setUserMessageId(message.getId());transition(row,"RUNNING",permit.ownerToken());
            String stage=input.answerMode()==AnswerMode.CONTINUE_HISTORY?"ANSWER":input.knowledgeBaseId()!=null?"KNOWLEDGE":"PLAN";
            if(chats.admitRun(permit.scope(),permit.runId(),stage)!=1)throw conflict();return message.getId();
        });
    }
    public void cancelOwned(Permit permit) {
        tx.executeWithoutResult(status-> {
            chats.lockAnyConversation(permit.scope());var row=chats.lock(permit.scope(),permit.identity().requestId());
            if(row!=null && "RUNNING".equals(row.getStatus()) && permit.ownerToken().equals(row.getOwnerToken()) && row.getCancelRequestedAt()==null) {
                row.setCancelRequestedAt(chats.now());transition(row,"RUNNING",permit.ownerToken());chats.cancelGeneration(permit.scope(),permit.identity().requestId());
            }
        });
    }

    public void checkActive(Permit permit) {
        verifyExecutionActor(permit);
        tx.executeWithoutResult(status -> {
            conversation(permit.scope()); var row = chats.lock(permit.scope(), permit.identity().requestId());
            requireActive(row, permit, chats.now());
            var generation = chats.active(permit.scope());
            if (generation == null || !"AGENT_CHAT".equals(generation.getEntryType()) || !permit.identity().requestId().equals(generation.getGenerationId())
                    || !permit.ownerToken().equals(generation.getOwnerToken()) || generation.getCancelRequestedAt() != null
                    || !generation.getLeaseUntil().after(chats.now()) || !generation.getDeadline().after(chats.now())) throw conflict();
        });
    }
    public void renew(Permit permit) {
        verifyExecutionActor(permit);
        tx.executeWithoutResult(status -> {
            conversation(permit.scope()); var row = chats.lock(permit.scope(), permit.identity().requestId()); Date now = chats.now();
            requireActive(row, permit, now); Date until = new Date(Math.min(plus(now, LEASE_SECONDS).getTime(), row.getDeadline().getTime()));
            if (chats.renew(permit.scope(), permit.identity().requestId(), permit.ownerToken(), until) != 1
                    || chats.renewGeneration(permit.scope(), permit.identity().requestId(), permit.ownerToken(), until) != 1) throw conflict();
        });
    }
    public void verifyActor(Permit permit) {
        if (permit == null) throw invalid(); var actor = actor();
        if (!Objects.equals(actor.tenant(), permit.scope().tenantId()) || !Objects.equals(actor.user(), permit.scope().actorUserId())
                || !Objects.equals(permit.scope().conversationId(), permit.identity().conversationId())) throw denied();
    }
    /** Provider/续约线程不借用旧 ThreadLocal 授权，使用未对外暴露的 permit 重新解析当前主体。 */
    private void verifyExecutionActor(Permit permit) {
        if(permit==null || !Objects.equals(permit.scope().conversationId(),permit.identity().conversationId()))throw denied();
        var actor=actors.requireActor(permit.scope().tenantId(),permit.scope().actorUserId());
        if(actor==null || !actor.isTenantAdmin() || !permit.scope().tenantId().toString().equals(actor.getTenantId())
                || !permit.scope().actorUserId().equals(actor.getUserId()))throw denied();
    }
    private void requireActive(AgentChatRequestRow row, Permit permit, Date now) {
        if (row == null || !Status.RUNNING.name().equals(row.getStatus()) || row.getCancelRequestedAt() != null
                || !Objects.equals(row.getOwnerToken(), permit.ownerToken()) || !Objects.equals(row.getRunId(), permit.runId())
                || row.getLeaseUntil() == null || !row.getLeaseUntil().after(now) || row.getDeadline() == null || !row.getDeadline().after(now)) throw conflict();
    }

    /** 仅收敛过期元数据；不补查工具，不接管或恢复任何外部调用。调用方已持会话及请求行锁。 */
    private void settle(Scope scope, AgentChatRequestRow row) {
        if (row == null) throw denied(); Date now = chats.now(); String previous = row.getStatus();
        if (Status.PREPARED.name().equals(previous) && !row.getExpiresAt().after(now)) {
            row.setStatus(Status.EXPIRED.name()); row.setFinishedTime(now); row.setErrorCode("CONFIRMATION_EXPIRED"); transition(row, previous, null);
        } else if (Status.RUNNING.name().equals(previous)
                && (row.getLeaseUntil() == null || !row.getLeaseUntil().after(now) || row.getDeadline() == null || !row.getDeadline().after(now))) {
            var run = chats.run(scope, row.getRequestId());
            boolean uncertain = run == null || !"NOT_DISPATCHED".equals(run.getDispatch())
                    || run.getToolCount() > 0 || run.getPlanCount() > 0 || run.getAnswerCount() > 0;
            row.setStatus(uncertain ? Status.NEEDS_ATTENTION.name() : row.getCancelRequestedAt() != null ? Status.CANCELLED.name() : Status.FAILED.name());
            row.setErrorCode(uncertain ? "RESULT_UNKNOWN" : row.getCancelRequestedAt() != null ? "CANCELLED" : "LEASE_EXPIRED");
            row.setFinishedTime(now); transition(row, previous, row.getOwnerToken());
            chats.failRun(scope, row.getRequestId(), row.getErrorCode()); chats.release(scope, row.getRequestId(), row.getOwnerToken());
        }
    }
    /** 仅用于同一真实会话的新请求收敛过期占用，不授予旧请求的读取或执行权限。 */
    public void retireExpired(Scope scope,String requestId) {
        tx.executeWithoutResult(status-> {if(chats.lockAnyConversation(scope)==null)throw denied();var row=chats.lock(scope,requestId);if(row!=null)settle(scope,row);});
    }
    private PreparedView view(AgentChatRequestRow row) {
        var selected = decode(row.getHistoryJson(), HistorySnapshot.class); var resources = decode(row.getSnapshotJson(), ResourceSnapshot.class);
        var scope=new Scope(row.getTenantId(),row.getActorUserId(),row.getConversationId());
        var run=row.getRunId()==null?null:chats.run(scope,row.getRequestId());
        RunSummary summary=run==null?null:new RunSummary(run.getPlanCount(),run.getAnswerCount(),run.getToolCount(),run.getStage(),run.getDispatch(),
                new RunUsage(run.getPlanInputTokens(),run.getPlanOutputTokens(),run.getPlanTotalTokens()),
                new RunUsage(run.getAnswerInputTokens(),run.getAnswerOutputTokens(),run.getAnswerTotalTokens()),
                chats.runCalls(scope,run.getRunId()).stream().map(call->new CallSummary(call.getCallNo(),call.getToolId(),call.getStatus(),call.getInvocationId(),call.getDispatch())).toList());
        return new PreparedView(row.getRequestId(), row.getStatus(), row.getAnswerMode(), row.getAgentId(), row.getModelId(),
                row.getMaxCalls(), row.getMaxWaitSeconds(), selected.turns().size(), selected.truncated(), selected.excludedTurns(),
                selected.turns().stream().flatMap(turn -> java.util.stream.Stream.of(turn.userMessageId(), turn.assistantMessageId())).toList(),
                resources.targets(), row.getExpiresAt(), row.getRunId(), row.getUserMessageId(), row.getAssistantMessageId(),
                row.getCancelRequestedAt() != null, row.getErrorCode(),summary);
    }
}
