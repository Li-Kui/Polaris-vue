package com.polaris.ai.agent.collaboration;

import com.polaris.ai.mapper.AgentChatMapper;
import com.polaris.ai.mapper.AgentRunMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 聊天许可由真实请求租约提供；复用预算表，不伪造工作流围栏或重放。 */
@Service
public class ChatAgentRunLedger {
    public interface Completion {
        void prepare(Result result);
        void commit(AgentChatRequestRow row,Result result);
    }
    private final AgentChatMapper chats;
    private final AgentRunMapper runs;
    private final AgentChatPreparationService preparation;
    private final AgentBindingService agents;
    private final AgentChatHistoryService history;
    private final AgentPlanValidator validator;
    private final TransactionTemplate tx;
    private final TransactionTemplate guardTx;
    public ChatAgentRunLedger(AgentChatMapper chats,AgentRunMapper runs,AgentChatPreparationService preparation,
            AgentBindingService agents,AgentChatHistoryService history,AgentCollaborationPorts.ToolArgumentValidator arguments,PlatformTransactionManager transactions) {
        this.chats=chats;this.runs=runs;this.preparation=preparation;this.agents=agents;this.history=history;validator=new AgentPlanValidator(arguments);
        tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 平台审计事务会先通过 control.check 持有会话锁；守卫必须加入该事务，
        // 不能挂起它后另开连接再争用同一行。预算/终态写入仍使用上面的独立事务。
        guardTx=new TransactionTemplate(transactions);
    }
    public AgentCollaborationPorts.RunLedger forExecution(Permit permit,Completion completion) {
        Objects.requireNonNull(completion);
        return (request,hash)-> {
            if(!AgentPlanValidator.requestHash(request).equals(hash))throw new Failure(Code.INVALID_REQUEST);
            tx.executeWithoutResult(status-> {
                var row=locked(permit);check(row,request.context());
                var resources=resources(row);var agent=agents.snapshot(row.getTenantId(),row.getActorUserId(),row.getAgentId());
                boolean required=AnswerMode.QUERY_EVIDENCE.name().equals(row.getAnswerMode()) && row.getKnowledgeBaseId()!=null;
                var selected=resources.tools().isEmpty()?List.<ToolBinding>of():agent.bindings();
                if(!permit.runId().equals(request.context().runId()) || !permit.ownerToken().equals(request.context().ownerToken())
                        || !agent.model().equals(request.model()) || !agent.rolePrompt().equals(request.rolePrompt()) || !selected.equals(request.tools())
                        || row.getUserMessageId()==null || resources.proof()==null || !resources.proof().questionHash().equals(digest(request.question()))
                        || !resources.proof().externalQueryHash().equals(digest(request.externalQuery())) || required!=request.knowledgeRequired()
                        || !request.evidencePolicy().name().equals(row.getAnswerMode()) || request.maxCalls()!=row.getMaxCalls()
                        || request.budget().toMillis()>row.getMaxWaitSeconds()*1000L
                        || !history.restore(permit.scope(),historySnapshot(row)).equals(request.history()))throw new Failure(Code.SNAPSHOT_CHANGED);
                if(chats.claimRun(permit.scope(),permit.runId(),hash)!=1)throw new Failure(Code.DUPLICATE_RUN);
            });
            return new Lease(request,permit,completion);
        };
    }
    public static ResourceSnapshot resources(AgentChatRequestRow row) {
        try {
            var result=AgentPlanValidator.JSON.readValue(row.getSnapshotJson(),ResourceSnapshot.class);
            if(result==null || !POLICY.equals(result.policy()) || !digest(result).equals(row.getSnapshotHash()))throw new Failure(Code.SNAPSHOT_CHANGED);
            return result;
        } catch(Failure safe) {throw safe;}catch(RuntimeException invalid) {throw new Failure(Code.SNAPSHOT_CHANGED);}
    }
    public static HistorySnapshot historySnapshot(AgentChatRequestRow row) {
        try {
            var result=AgentPlanValidator.JSON.readValue(row.getHistoryJson(),HistorySnapshot.class);
            if(result==null || !digest(result).equals(row.getHistoryHash()))throw new Failure(Code.SNAPSHOT_CHANGED);
            return result;
        } catch(Failure safe) {throw safe;}catch(RuntimeException invalid) {throw new Failure(Code.SNAPSHOT_CHANGED);}
    }
    private Permit permit(Context context,AgentChatRequestRow row) {
        if(!(context.entry() instanceof AgentEntryIdentity.Chat identity))throw new Failure(Code.ACCESS_DENIED);
        return new Permit(new Scope(context.tenantId(),context.actorUserId(),identity.conversationId()),identity,context.runId(),context.ownerToken(),row.getDeadline());
    }
    private AgentChatRequestRow locked(Permit permit) {
        if(chats.lockConversation(permit.scope())==null)throw new Failure(Code.ACCESS_DENIED);
        var row=chats.lock(permit.scope(),permit.identity().requestId());
        if(row==null)throw new Failure(Code.ACCESS_DENIED);return row;
    }
    private void check(AgentChatRequestRow row,Context context) {
        var permit=permit(context,row);
        try {preparation.checkActive(permit);}
        catch(com.polaris.common.exception.ServiceException inactive) {
            if(row.getCancelRequestedAt()!=null)throw new Failure(Code.CANCELLED);
            if(row.getDeadline()!=null && !row.getDeadline().after(chats.now()))throw new Failure(Code.DEADLINE_EXCEEDED);
            if(row.getLeaseUntil()!=null && !row.getLeaseUntil().after(chats.now()))throw new Failure(Code.CANCELLED);
            throw new Failure(Code.ACCESS_DENIED);
        }
        var frozen=resources(row);var current=agents.snapshot(context.tenantId(),context.actorUserId(),context.agentId());
        if(!context.agentId().equals(row.getAgentId()) || !context.definitionHash().equals(frozen.agentHash())
                || !current.definitionHash().equals(frozen.agentHash()) || !current.model().modelId().equals(frozen.modelId())
                || !current.model().definitionHash().equals(frozen.modelHash()))throw new Failure(Code.SNAPSHOT_CHANGED);
        history.restore(permit.scope(),historySnapshot(row));
    }
    private String argumentHash(String args) {return digest(AgentPlanValidator.sorted(AgentPlanValidator.parseObject(args,8192,Code.ARGUMENTS_REJECTED)));}
    public void checkReserved(ToolIntent intent) {
        var context=intent.context();
        if(!(context.entry() instanceof AgentEntryIdentity.Chat identity))throw new Failure(Code.ACCESS_DENIED);
        var scope=new Scope(context.tenantId(),context.actorUserId(),identity.conversationId());
        guardTx.executeWithoutResult(status-> {
            var row=chats.get(scope.tenantId(),scope.actorUserId(),identity.requestId());if(row==null)throw new Failure(Code.ACCESS_DENIED);
            check(row,context);var run=runs.get(scope.tenantId(),context.runId());
            if(run==null || !"RUNNING".equals(run.getStatus()) || run.getAnswerCount()!=0)throw new Failure(Code.ACCESS_DENIED);
            var call=runs.calls(scope.tenantId(),context.runId()).stream().filter(c->c.getCallNo()==intent.callNo()).findFirst()
                    .orElseThrow(()->new Failure(Code.ACCESS_DENIED));var tool=intent.tool();
            if(!"RESERVED".equals(call.getStatus()) || !call.getToolId().equals(tool.toolId()) || !call.getToolRevision().equals(tool.revision())
                    || !call.getConnectionRevision().equals(tool.connectionRevision()) || !call.getCatalogRevision().equals(tool.catalogRevision())
                    || !call.getStructureHash().equals(tool.structureHash()) || !call.getArgumentsHash().equals(argumentHash(intent.argumentsJson())))
                throw new Failure(Code.ACCESS_DENIED);
        });
    }
    private final class Lease implements AgentCollaborationPorts.RunLedger.Lease {
        private final Request request;private final Permit permit;private final Completion completion;private boolean ended;
        Lease(Request request,Permit permit,Completion completion) {this.request=request;this.permit=permit;this.completion=completion;}
        private void mutate(BiConsumer<AgentRunRow,AgentChatRequestRow> operation) {
            tx.executeWithoutResult(status-> {
                var row=locked(permit);check(row,request.context());var run=runs.lock(permit.scope().tenantId(),permit.runId());
                if(run==null || !"RUNNING".equals(run.getStatus()))throw new Failure(Code.DUPLICATE_RUN);
                operation.accept(run,row);if(chats.updateRun(run)!=1)throw new Failure(Code.AUDIT_INCOMPLETE);
            });
        }
        @Override public void checkActive() {var row=chats.get(permit.scope().tenantId(),permit.scope().actorUserId(),permit.identity().requestId());if(row==null)throw new Failure(Code.ACCESS_DENIED);check(row,request.context());}
        @Override public void reserveModel(Stage stage) {
            mutate((run,row)-> {
                if(stage==Stage.PLAN && run.getPlanCount()==0 && run.getAnswerCount()==0 && run.getToolCount()==0)run.setPlanCount(1);
                else if(stage==Stage.ANSWER && run.getAnswerCount()==0 && (request.tools().isEmpty() || Boolean.TRUE.equals(run.getPlanUsageRecorded()))
                        && runs.calls(run.getTenantId(),run.getRunId()).stream().allMatch(c->"SUCCESS".equals(c.getStatus())))run.setAnswerCount(1);
                else throw new Failure(Code.BUDGET_EXCEEDED);run.setStage(stage.name());
            });
        }
        @Override public void recordModelUsage(Stage stage,Usage usage) {
            mutate((run,row)-> {
                if(stage==Stage.PLAN && run.getPlanCount()==1 && !Boolean.TRUE.equals(run.getPlanUsageRecorded()) && run.getAnswerCount()==0) {
                    run.setPlanUsageRecorded(true);run.setPlanInputTokens(number(usage.inputTokens()));run.setPlanOutputTokens(number(usage.outputTokens()));run.setPlanTotalTokens(number(usage.totalTokens()));
                } else if(stage==Stage.ANSWER && run.getAnswerCount()==1 && !Boolean.TRUE.equals(run.getAnswerUsageRecorded())) {
                    run.setAnswerUsageRecorded(true);run.setAnswerInputTokens(number(usage.inputTokens()));run.setAnswerOutputTokens(number(usage.outputTokens()));run.setAnswerTotalTokens(number(usage.totalTokens()));
                } else throw new Failure(Code.AUDIT_INCOMPLETE);
            });
        }
        @Override public void reserveTool(ToolIntent intent) {
            if(!request.context().equals(intent.context()))throw new Failure(Code.ACCESS_DENIED);
            var binding=request.tools().stream().filter(t->t.snapshot().equals(intent.tool())).findFirst().orElseThrow(()->new Failure(Code.ACCESS_DENIED));
            validator.validate(request,new Plan(List.of(new Proposal("reservation",binding.alias(),intent.argumentsJson())),Usage.unknown()));
            mutate((run,row)-> {
                var calls=runs.calls(run.getTenantId(),run.getRunId());var hash=argumentHash(intent.argumentsJson());
                if(run.getPlanCount()!=1 || !Boolean.TRUE.equals(run.getPlanUsageRecorded()) || run.getAnswerCount()!=0
                        || intent.callNo()!=run.getToolCount()+1 || intent.callNo()>run.getMaxCalls()
                        || calls.stream().anyMatch(c->!"SUCCESS".equals(c.getStatus()) || c.getToolId().equals(intent.tool().toolId()) && c.getArgumentsHash().equals(hash)))
                    throw new Failure(Code.BUDGET_EXCEEDED);
                var call=new AgentRunCallRow();call.setTenantId(run.getTenantId());call.setRunId(run.getRunId());call.setCallNo(intent.callNo());
                call.setToolId(intent.tool().toolId());call.setToolRevision(intent.tool().revision());call.setConnectionRevision(intent.tool().connectionRevision());
                call.setCatalogRevision(intent.tool().catalogRevision());call.setStructureHash(intent.tool().structureHash());call.setArgumentsHash(hash);
                if(runs.insertCall(call)!=1)throw new Failure(Code.AUDIT_INCOMPLETE);run.setToolCount(intent.callNo());run.setStage(Stage.TOOL.name());
            });
        }
        @Override public void recordTool(int no,String invocationId,boolean succeeded,Dispatch dispatch) {
            mutate((run,row)-> {
                var call=runs.calls(run.getTenantId(),run.getRunId()).stream().filter(c->c.getCallNo()==no).findFirst().orElseThrow(()->new Failure(Code.AUDIT_INCOMPLETE));
                id(invocationId);call.setInvocationId(invocationId);call.setStatus(succeeded?"SUCCESS":"FAILED");call.setDispatch(dispatch.name());
                if(runs.finishCall(call)!=1)throw new Failure(Code.AUDIT_INCOMPLETE);run.setDispatch(dispatch.name());
            });
        }
        @Override public void complete(List<ToolSource> sources) {throw new Failure(Code.INVALID_REQUEST);}
        @Override public void complete(Result result) {
            completion.prepare(result); // 审核不占用数据库事务。
            mutate((run,row)-> {
                var calls=runs.calls(run.getTenantId(),run.getRunId());
                if(!Boolean.TRUE.equals(run.getAnswerUsageRecorded()) || calls.size()!=result.toolSources().size()
                        || calls.stream().anyMatch(c->!"SUCCESS".equals(c.getStatus()) || result.toolSources().stream().noneMatch(s->s.callNo()==c.getCallNo() && s.invocationId().equals(c.getInvocationId()))))
                    throw new Failure(Code.AUDIT_INCOMPLETE);
                completion.commit(row,result);run.setStatus("SUCCEEDED");run.setFinishedTime(chats.now());
                row.setStatus(Status.SUCCEEDED.name());row.setFinishedTime(run.getFinishedTime());
                if(chats.transition(row,"RUNNING",permit.ownerToken())!=1 || chats.release(permit.scope(),permit.identity().requestId(),permit.ownerToken())!=1)
                    throw new Failure(Code.AUDIT_INCOMPLETE);
            });ended=true;
        }
        @Override public void fail(Code code,Dispatch dispatch) {ChatAgentRunLedger.this.fail(permit,code,dispatch);ended=true;}
        @Override public void close() {if(!ended)fail(Code.RESULT_UNKNOWN,Dispatch.UNKNOWN);}
    }
    /** 领取后审核/排队/组装失败也需收尾；终态和其他持有者绝不被迟到异常覆盖。 */
    public void fail(Permit permit,Code code,Dispatch dispatch) {
        boolean interrupted=Thread.interrupted();
        try {
            tx.executeWithoutResult(status-> {
                chats.lockAnyConversation(permit.scope());var row=chats.lock(permit.scope(),permit.identity().requestId());
                if(row==null || !"RUNNING".equals(row.getStatus()) || !permit.ownerToken().equals(row.getOwnerToken()))return;
                var run=runs.lock(permit.scope().tenantId(),permit.runId());boolean unknown=dispatch!=Dispatch.NOT_DISPATCHED
                        || run==null || run.getToolCount()>0 || run.getPlanCount()>0 && !Boolean.TRUE.equals(run.getPlanUsageRecorded())
                        || run.getAnswerCount()>0 && !Boolean.TRUE.equals(run.getAnswerUsageRecorded());
                row.setStatus(unknown?Status.NEEDS_ATTENTION.name():row.getCancelRequestedAt()!=null || code==Code.CANCELLED?Status.CANCELLED.name():Status.FAILED.name());
                row.setErrorCode(code.name());row.setFinishedTime(chats.now());
                if(chats.transition(row,"RUNNING",permit.ownerToken())!=1)throw new Failure(Code.AUDIT_INCOMPLETE);
                if(run!=null) {run.setStatus("FAILED");run.setErrorCode(code.name());run.setDispatch(dispatch.name());run.setFinishedTime(row.getFinishedTime());if(chats.updateRun(run)!=1)throw new Failure(Code.AUDIT_INCOMPLETE);}
                chats.release(permit.scope(),permit.identity().requestId(),permit.ownerToken());
            });
        } finally {if(interrupted)Thread.currentThread().interrupt();}
    }
    private static Long number(Integer value) {return value==null?null:value.longValue();}
}
