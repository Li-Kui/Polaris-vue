package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.mapper.AgentChatMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;

/** 普通租户聊天共用会话占用；管理后台会话无租户，不会进入租户协作入口。 */
@Service
public class ChatGenerationFence {
    private final AgentChatMapper chats;
    private final AgentChatPreparationService agentRequests;
    private final TransactionTemplate tx;
    private final ScheduledExecutorService scheduler;
    public ChatGenerationFence(AgentChatMapper chats,AgentChatPreparationService agentRequests,PlatformTransactionManager transactions,
            @Qualifier("scheduledExecutorService") ScheduledExecutorService scheduler) {
        this.chats=chats;this.agentRequests=agentRequests;tx=new TransactionTemplate(transactions);this.scheduler=scheduler;
    }
    private Scope scope(Long conversation,Long user) {
        var caller=CallerContextHolder.require();if(!caller.isPlatformMode())return null;
        if(!Objects.equals(user,caller.getUserId()))throw denied();return new Scope(Long.valueOf(caller.getTenantId()),user,conversation);
    }
    public Frame enter(Long conversation,Long user,AtomicBoolean cancelled,Runnable cancelAction) {
        var scope=scope(conversation,user);if(scope==null)return null;
        var generation=tx.execute(status-> {
            if(chats.lockConversation(scope)==null)throw denied();var old=chats.active(scope);Date now=chats.now();
            if(old!=null) {
                if(old.getLeaseUntil().after(now) && old.getDeadline().after(now))throw new com.polaris.common.exception.ServiceException("当前会话正在生成，请先停止或等待完成",409);
                if("AGENT_CHAT".equals(old.getEntryType()))agentRequests.retireExpired(scope,old.getGenerationId());
                chats.release(scope,old.getGenerationId(),old.getOwnerToken());
            }
            var row=new ChatGenerationRow();row.setTenantId(scope.tenantId());row.setActorUserId(scope.actorUserId());row.setConversationId(scope.conversationId());
            row.setEntryType("DIRECT_CHAT");row.setGenerationId(UUID.randomUUID().toString());row.setOwnerToken(UUID.randomUUID().toString());
            row.setLeaseUntil(new Date(now.getTime()+LEASE_SECONDS*1000L));row.setDeadline(new Date(now.getTime()+180000));
            if(chats.occupy(row)!=1)throw conflict();return row;
        });
        var frame=new Frame(scope,generation,cancelled,cancelAction);
        try {frame.task=scheduler.scheduleWithFixedDelay(frame::tick,HEARTBEAT_SECONDS,HEARTBEAT_SECONDS,TimeUnit.SECONDS);}
        catch(RuntimeException unavailable) {frame.close();throw unavailable;}
        return frame;
    }
    public boolean cancel(Long conversation,Long user) {
        var scope=scope(conversation,user);if(scope==null)return false;
        return Boolean.TRUE.equals(tx.execute(status-> {
            if(chats.lockConversation(scope)==null)return false;var row=chats.active(scope);
            return row!=null && "DIRECT_CHAT".equals(row.getEntryType()) && chats.cancelGeneration(scope,row.getGenerationId())==1;
        }));
    }
    public void cancelForDeletion(Long conversation,Long user) {
        var scope=scope(conversation,user);if(scope==null)return;
        tx.executeWithoutResult(status-> {
            if(chats.lockConversation(scope)==null)return;var row=chats.active(scope);
            if(row!=null)chats.cancelGeneration(scope,row.getGenerationId());chats.cancelForDeletion(scope);
        });
    }
    public final class Frame implements AutoCloseable {
        private final Scope scope;private final ChatGenerationRow row;private final AtomicBoolean cancelled;private final Runnable action;
        private final AtomicBoolean closed=new AtomicBoolean();private volatile ScheduledFuture<?> task;
        Frame(Scope scope,ChatGenerationRow row,AtomicBoolean cancelled,Runnable action) {this.scope=scope;this.row=row;this.cancelled=cancelled;this.action=action;}
        public boolean owns(AtomicBoolean value) {return cancelled==value;}
        public void check() {
            if(closed.get() || cancelled.get())throw new CancellationException();
            tx.executeWithoutResult(status-> {
                if(chats.lockConversation(scope)==null)throw new CancellationException();var current=chats.active(scope);Date now=chats.now();
                if(current==null || !"DIRECT_CHAT".equals(current.getEntryType()) || !row.getGenerationId().equals(current.getGenerationId())
                        || !row.getOwnerToken().equals(current.getOwnerToken()) || current.getCancelRequestedAt()!=null
                        || !current.getLeaseUntil().after(now) || !current.getDeadline().after(now))throw new CancellationException();
            });
        }
        private void tick() {
            if(closed.get())return;
            try {
                check();tx.executeWithoutResult(status-> {
                    if(chats.lockConversation(scope)==null)throw new CancellationException();
                    Date until=new Date(Math.min(chats.now().getTime()+LEASE_SECONDS*1000L,row.getDeadline().getTime()));
                    if(chats.renewGeneration(scope,row.getGenerationId(),row.getOwnerToken(),until)!=1)throw new CancellationException();
                });
            } catch(RuntimeException inactive) {cancelled.set(true);try {action.run();}finally {close();}}
        }
        @Override public void close() {
            if(!closed.compareAndSet(false,true))return;if(task!=null)task.cancel(false);
            tx.executeWithoutResult(status-> {chats.lockAnyConversation(scope);chats.release(scope,row.getGenerationId(),row.getOwnerToken());});
        }
    }
}
