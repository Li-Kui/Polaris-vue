package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.*;
import com.polaris.ai.knowledge.KnowledgeRetrievalService;
import com.polaris.ai.mapper.*;
import com.polaris.ai.modelcenter.runtime.ChatRuntimeService;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.ai.safety.guard.IModeratedInputPreparationService;
import com.polaris.ai.safety.model.ModerationScene;
import com.polaris.ai.safety.service.IModerationFacade;
import com.polaris.ai.service.IAiKnowledgeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.context.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;
import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;

/** 本次执行在受控线程池中完成；无独立队列、重试、自动接管或恢复循环。 */
@Service
public class AgentChatExecutionService {
    public record Start(String confirmationId,Input input) {
        public Start {id(confirmationId);if(input==null)throw invalid();}
        @Override public String toString() {return "AgentChatStart[confirmationId="+confirmationId+"]";}
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String key,Object value) {throw invalid();}
    }
    public record Admission(PreparedView existing,Session session) { }
    private final AgentChatPreparationService preparation;
    private final AgentChatMapper chats;
    private final AgentRunMapper runs;
    private final AgentBindingService agents;
    private final AgentToolCatalog actors;
    private final AgentChatHistoryService history;
    private final AgentChatSourceGuard sources;
    private final AgentKnowledgeSnapshotService knowledgeSnapshots;
    private final IAiKnowledgeService knowledgeService;
    private final AiVectorStoreResolver vectors;
    private final KnowledgeRetrievalService retrieval;
    private final ChatAgentRunLedger ledger;
    private final ChatRuntimeService runtime;
    private final AgentCollaborationPorts.ExternalReadToolGateway tools;
    private final AgentPlanValidator plans;
    private final IModeratedInputPreparationService inputModeration;
    private final IModerationFacade outputModeration;
    private final TaskExecutor executor;
    private final ScheduledExecutorService scheduler;
    public AgentChatExecutionService(AgentChatPreparationService preparation,AgentChatMapper chats,AgentRunMapper runs,
            AgentBindingService agents,AgentToolCatalog actors,AgentChatHistoryService history,AgentChatSourceGuard sources,
            AgentKnowledgeSnapshotService knowledgeSnapshots,IAiKnowledgeService knowledgeService,AiVectorStoreResolver vectors,
            KnowledgeRetrievalService retrieval,ChatAgentRunLedger ledger,ChatRuntimeService runtime,
            AgentCollaborationPorts.ExternalReadToolGateway tools,AgentCollaborationPorts.ToolArgumentValidator arguments,
            IModeratedInputPreparationService inputModeration,IModerationFacade outputModeration,
            @Qualifier("aiTaskExecutor")TaskExecutor executor,@Qualifier("scheduledExecutorService")ScheduledExecutorService scheduler) {
        this.preparation=preparation;this.chats=chats;this.runs=runs;this.agents=agents;this.actors=actors;this.history=history;this.sources=sources;
        this.knowledgeSnapshots=knowledgeSnapshots;this.knowledgeService=knowledgeService;this.vectors=vectors;this.retrieval=retrieval;
        this.ledger=ledger;this.runtime=runtime;this.tools=tools;plans=new AgentPlanValidator(arguments);
        this.inputModeration=inputModeration;this.outputModeration=outputModeration;this.executor=executor;this.scheduler=scheduler;
    }
    public Admission admit(Start start) {
        var existing=preparation.submission(start.input(),start.confirmationId());
        if(!"PREPARED".equals(existing.status()))return new Admission(existing,null);
        try {return new Admission(null,new Session(start.input(),preparation.claim(start.input(),start.confirmationId()),SecurityContextHolder.getContext()));}
        catch(com.polaris.common.exception.ServiceException conflict) {
            var current=preparation.submission(start.input(),start.confirmationId());
            if(!"PREPARED".equals(current.status()))return new Admission(current,null);throw conflict;
        }
    }
    public final class Session {
        private final Input input;private final Permit permit;private final SecurityContext security;
        private final AtomicBoolean cancelled=new AtomicBoolean();private boolean finished,started;
        private Thread worker;private ScheduledFuture<?> heartbeat;
        Session(Input input,Permit permit,SecurityContext security) {this.input=input;this.permit=permit;this.security=security;}
        public synchronized void cancel() {
            if(finished)return;
            // 先提交取消标记，避免执行端看到内存标记后抢先写终态，导致取消未留痕。
            try {preparation.cancelOwned(permit);}finally {cancelled.set(true);if(worker!=null)worker.interrupt();}
        }
        public synchronized void finish() {
            if(finished)return;finished=true;worker=null;if(heartbeat!=null)heartbeat.cancel(false);
        }
        public void start(BiConsumer<String,Object> events,Runnable close) {
            synchronized(this) {if(started)throw new Failure(Code.DUPLICATE_RUN);started=true;}
            try {
                heartbeat=scheduler.scheduleWithFixedDelay(()-> {
                    synchronized(this) {if(finished)return;}
                    try {preparation.renew(permit);}catch(RuntimeException inactive) {try {cancel();}catch(RuntimeException ignored) {cancelled.set(true);}}
                },HEARTBEAT_SECONDS,HEARTBEAT_SECONDS,TimeUnit.SECONDS);
                executor.execute(()->run(events,close));
            } catch(RuntimeException rejected) {
                ledger.fail(permit,Code.BUDGET_EXCEEDED,Dispatch.NOT_DISPATCHED);finish();close.run();throw rejected;
            }
        }
        private void run(BiConsumer<String,Object> events,Runnable close) {
            var previous=CallerContextHolder.get();var previousSecurity=SecurityContextHolder.getContext();
            synchronized(this) {worker=Thread.currentThread();}
            try {
                CallerContextHolder.set(actors.requireActor(permit.scope().tenantId(),permit.scope().actorUserId()));SecurityContextHolder.setContext(security);
                check();events.accept("started",Map.of("requestId",permit.identity().requestId(),"runId",permit.runId()));
                events.accept("stage",Map.of("stage","INPUT_REVIEW"));
                String review=input.question()+(input.externalQuery().isEmpty()?"":"\n本轮外发查询：\n"+input.externalQuery());
                var approved=inputModeration.prepare(ModerationScene.CHAT_INPUT,review,List.of(),permit.scope().actorUserId(),"AI_CONVERSATION",input.conversationId().toString());
                if(approved==null || !approved.isAllowed())throw new Failure(Code.MODERATION_BLOCKED);
                check();Long userMessageId=preparation.admitUser(permit,input,approved.moderation());
                events.accept("user_message_saved",Map.of("messageId",userMessageId));
                var row=chats.get(permit.scope().tenantId(),permit.scope().actorUserId(),permit.identity().requestId());
                var frozen=ChatAgentRunLedger.resources(row);var frozenHistory=ChatAgentRunLedger.historySnapshot(row);
                var prior=history.restore(permit.scope(),frozenHistory);var agent=agents.snapshot(permit.scope().tenantId(),permit.scope().actorUserId(),input.agentId());
                StringBuilder context=new StringBuilder();
                for(var turn:prior.subList(Math.max(0,prior.size()-2),prior.size())) {if(!context.isEmpty())context.append('\n');String text=turn.question();int n=Math.min(1000,text.length());if(n<text.length() && Character.isHighSurrogate(text.charAt(n-1)))n--;context.append(text,0,n);}
                Long knowledgeId=input.answerMode()==AnswerMode.QUERY_EVIDENCE?input.knowledgeBaseId():null;
                var knowledge=new ChatAgentKnowledgeGateway(permit.scope(),knowledgeId,frozen.knowledgeHash(),context.toString(),knowledgeSnapshots,knowledgeService,vectors,retrieval,actors);
                var completion=new AgentChatResultCommitter(permit,frozenHistory,chats,runs,sources,knowledge,outputModeration,approved.moderation());
                var model=new RuntimeAgentModelGateway(runtime).withOverrides(agent.modelOverrides());
                var modelEvents=new AgentCollaborationPorts.ModelGateway() {
                    public void validate(ModelSelection selected,List<ToolDefinition> definitions) {model.validate(selected,definitions);}
                    public Plan plan(PlanningInput plan,AgentCallControl control) {events.accept("stage",Map.of("stage","PLAN"));return model.plan(plan,control);}
                    public Answer answer(AnswerInput answer,AgentCallControl control) {events.accept("stage",Map.of("stage","ANSWER"));return model.answer(answer,control);}
                };
                var toolEvents=new AgentCollaborationPorts.ExternalReadToolGateway() {
                    public void validateBindings(Context selected,List<ToolBinding> bindings) {tools.validateBindings(selected,bindings);}
                    public ToolResult invoke(ToolIntent intent,AgentCallControl control) {events.accept("stage",Map.of("stage","TOOL","callNo",intent.callNo()));return tools.invoke(intent,control);}
                };
                var knowledgeEvents=new AgentCollaborationPorts.KnowledgeGateway() {
                    public List<KnowledgeEvidence> retrieve(Request request,AgentCallControl control) {events.accept("stage",Map.of("stage","KNOWLEDGE"));return knowledge.retrieve(request,control);}
                    public void recheck(Context context,List<KnowledgeEvidence> evidence) {knowledge.recheck(context,evidence);}
                };
                var contextIdentity=new Context(permit.runId(),permit.scope().tenantId(),permit.scope().actorUserId(),input.agentId(),permit.identity(),frozen.agentHash(),permit.ownerToken());
                var request=new Request(contextIdentity,agent.model(),agent.rolePrompt(),input.question(),input.externalQuery(),knowledgeId!=null,
                        frozen.tools().isEmpty()?List.of():agent.bindings(),row.getMaxCalls(),Duration.ofMillis(Math.max(100,row.getDeadline().getTime()-chats.now().getTime())),
                        EvidencePolicy.valueOf(input.answerMode().name()),prior);
                var coordinator=new BoundedAgentCoordinator(modelEvents,toolEvents,knowledgeEvents,ledger.forExecution(permit,completion),plans);
                var result=coordinator.execute(request,()-> {if(cancelled.get())throw new Failure(Code.CANCELLED);knowledge.checkCurrent();});
                events.accept("message_saved",Map.of("messageId",completion.messageId()));
                events.accept("message",Map.of("text",result.text()));
                events.accept("sources",Map.of("knowledge",knowledge.citations(),"tools",result.toolSources(),"history",completion.dependencies().history(),"historyMessageIds",result.historyMessageIds()));
                events.accept("done",preparation.get(permit.identity().requestId()));
            } catch(RuntimeException failed) {
                Code code=failed instanceof Failure safe?safe.code():cancelled.get() || Thread.currentThread().isInterrupted()?Code.CANCELLED:Code.MODEL_FAILED;
                Dispatch dispatch=failed instanceof Failure safe?safe.dispatch():Dispatch.NOT_DISPATCHED;
                boolean interrupted=Thread.interrupted();
                try {ledger.fail(permit,code,dispatch);try {events.accept("error",Map.of("code","AGENT_"+code.name(),"requestId",permit.identity().requestId()));}catch(RuntimeException disconnected) { /* 终态从只读接口读取。 */ }}
                finally {if(interrupted)Thread.currentThread().interrupt();}
            } finally {
                finish();CallerContextHolder.set(previous);SecurityContextHolder.setContext(previousSecurity);Thread.interrupted();close.run();
            }
        }
        private void check() {if(cancelled.get() || Thread.currentThread().isInterrupted())throw new Failure(Code.CANCELLED);preparation.checkActive(permit);}
    }
}
