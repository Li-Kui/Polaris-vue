package com.polaris.ai.knowledge;

import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.KnowledgeVectorCleanupMapper;
import com.polaris.ai.mapper.KnowledgeVectorCollectionMapper;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 单集合显式清理；结果不明不重新派发，远端 I/O 全部在数据库事务之外。 */
@Service
public class KnowledgeVectorCleanupService {
    private final KnowledgeVectorCollectionRegistry registry;
    private final KnowledgeVectorCollectionMapper collections;
    private final KnowledgeVectorCleanupMapper cleanup;
    private final AiVectorStoreResolver resolver;
    private final TransactionTemplate transactions;
    private final AtomicInteger active=new AtomicInteger();

    public KnowledgeVectorCleanupService(KnowledgeVectorCollectionRegistry registry,KnowledgeVectorCollectionMapper collections,
            KnowledgeVectorCleanupMapper cleanup,AiVectorStoreResolver resolver,PlatformTransactionManager manager) {
        this.registry=registry;this.collections=collections;this.cleanup=cleanup;this.resolver=resolver;
        this.transactions=new TransactionTemplate(manager);transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Plan plan(Long id,Long generation) {
        var source=registry.accessible(id);validateGeneration(generation);
        return transactions.execute(status -> {
            var parent=parent(source);var row=row(parent,generation);var now=collections.databaseNow();
            String reason=reason(parent,row,now);
            if (!"LOCAL_CANDIDATE".equals(reason)) return new Plan(row.getIndexVersion(),generation,null,null,true,reason);
            long expires=now.getTime()+300_000L;
            return new Plan(row.getIndexVersion(),generation,planKey(parent,row,expires),new Date(expires),false,null);
        });
    }

    public View status(Long id,Long generation) {
        var source=registry.accessible(id);validateGeneration(generation);
        return transactions.execute(status -> view(row(parent(source),generation),collections.databaseNow()));
    }

    public View execute(Long id,Long generation,Request request) {
        var source=registry.accessible(id);validateGeneration(generation);
        if (request==null || !Boolean.TRUE.equals(request.confirmed()) || !Boolean.TRUE.equals(request.controlledStorageConfirmed())
                || request.planId()==null || !request.planId().matches("[0-9]{13}_[a-f0-9]{64}"))
            throw new ServiceException("请先预览，并确认删除不可恢复及受控存储操作窗口",400);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new ServiceException("清理执行不能在数据库事务中调用",409);
        if (active.incrementAndGet()>4) {active.decrementAndGet();throw new ServiceException("清理正在进行，请稍后手动重试",429);}
        try {
            String owner=UUID.randomUUID().toString();
            Claim claim=transactions.execute(status -> {
                var parent=parent(source);var row=row(parent,generation);var now=collections.databaseNow();
                // 同一计划重发只返回已保存状态，包括结果不明/到期状态；绝不再发一次 DELETE。
                if (Objects.equals(request.planId(),row.getCleanupPlanKey()) && !"NONE".equals(row.getCleanupState())) return new Claim(parent,row,false);
                long expires=Long.parseLong(request.planId().substring(0,13));
                if (expires<=now.getTime() || expires>now.getTime()+300_000L || !"LOCAL_CANDIDATE".equals(reason(parent,row,now))
                        || !request.planId().equals(planKey(parent,row,expires))) throw changed();
                if (cleanup.claim(parent,row,owner,request.planId(),com.polaris.ai.core.context.CallerContextHolder.require().getUserId())!=1) throw changed();
                return new Claim(parent,row(parent,generation),true);
            });
            if (!claim.created()) return view(claim.row(),collections.databaseNow());
            // claim 已提交。异常/进程退出保留 RUNNING，不擅自恢复 owner 或猜测远端结果。
            AiVectorStoreResolver.CollectionDeletion result;
            try {result=resolver.deleteOwnedCollection(claim.row().getCollectionName(),claim.row().getOwnershipMarker(),()->dispatch(claim,owner));}
            catch(RuntimeException e) {result=AiVectorStoreResolver.CollectionDeletion.DELETE_UNCONFIRMED;}
            var outcome=result;
            return transactions.execute(status -> {
                var parent=parent(claim.parent());var row=row(parent,generation);var now=collections.databaseNow();
                if (!owned(row,claim.row(),owner,now)) return view(row,now);
                boolean deleted=outcome==AiVectorStoreResolver.CollectionDeletion.DELETED && Boolean.TRUE.equals(row.getCleanupDispatched());
                boolean retry=outcome==AiVectorStoreResolver.CollectionDeletion.PRECHECK_UNAVAILABLE
                        && !Boolean.TRUE.equals(row.getCleanupDispatched()) && row.getCleanupAttempts()<5;
                String state=deleted?"SUCCEEDED":retry?"RETRY_WAIT":"MANUAL_REQUIRED";
                String error=deleted?null:outcome==AiVectorStoreResolver.CollectionDeletion.PRECHECK_UNAVAILABLE && !Boolean.TRUE.equals(row.getCleanupDispatched())?"REMOTE_PRECHECK_UNAVAILABLE"
                        :outcome==AiVectorStoreResolver.CollectionDeletion.OWNERSHIP_REJECTED?"OWNERSHIP_NOT_MATCHED"
                        :outcome==AiVectorStoreResolver.CollectionDeletion.PRECHECK_CHANGED?"CLEANUP_SCOPE_CHANGED":"DELETE_RESULT_UNKNOWN";
                if (cleanup.finish(parent,row,owner,state,error)!=1) throw changed();
                if (deleted) cleanup.retireSnapshots(parent,KnowledgeDocumentSnapshotStore.digest(row.getCollectionName()));
                return view(row(parent,generation),collections.databaseNow());
            });
        } finally {active.decrementAndGet();}
    }

    /** 仅标记过期任务为需人工处理，不查询/重新删除远端，不声称不存在或已成功。 */
    public View stopExpired(Long id,Long generation,StopRequest request) {
        var source=registry.accessible(id);validateGeneration(generation);
        if (request==null || !Boolean.TRUE.equals(request.confirmed())) throw new ServiceException("请明确确认转人工核对，不会重新删除远端",400);
        return transactions.execute(status -> {
            var parent=parent(source);var row=row(parent,generation);
            if (!"RUNNING".equals(row.getCleanupState()) || row.getCleanupLeaseUntil()==null
                    || row.getCleanupLeaseUntil().after(collections.databaseNow()) || cleanup.stopExpired(parent,row)!=1) throw changed();
            return view(row(parent,generation),collections.databaseNow());
        });
    }

    private boolean dispatch(Claim claim,String owner) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            var parent=parent(claim.parent());var row=row(parent,claim.row().getBuildGeneration());var now=collections.databaseNow();
            if (!owned(row,claim.row(),owner,now) || !sameParent(parent,claim.parent()) || Boolean.TRUE.equals(row.getCleanupDispatched())
                    || !"LOCAL_CANDIDATE".equals(registry.localCleanupReason(parent,row,now,after(row),busy(parent)))) return false;
            return cleanup.dispatch(parent,row,owner)==1;
        }));
    }
    private String reason(AiKnowledgeBase parent,KnowledgeVectorCollection row,Date now) {return registry.cleanupReason(parent,row,now,after(row),busy(parent));}
    private boolean busy(AiKnowledgeBase parent) {return "BUILDING".equals(parent.getIndexStatus()) || collections.hasActiveWork(parent);}
    private Date after(KnowledgeVectorCollection row) {
        return registry.cleanupEligibleAfter(row);
    }
    private AiKnowledgeBase parent(AiKnowledgeBase source) {
        var parent=collections.lockParent(source);
        if (parent==null || !Objects.equals(parent.getTenantId(),source.getTenantId())) throw changed();
        return parent;
    }
    private KnowledgeVectorCollection row(AiKnowledgeBase parent,long generation) {
        var row=cleanup.lock(parent,generation);
        if (row==null || !Objects.equals(row.getTenantId(),parent.getTenantId()) || !Objects.equals(row.getKnowledgeBaseId(),parent.getId()) || !Objects.equals(row.getBuildGeneration(),generation))
            throw new ServiceException("集合登记不存在或无权访问",404);
        if (row.getCleanupState()==null || row.getCleanupAttempts()==null) throw new ServiceException("请先执行 SQL 29 并重启后端",409);
        return row;
    }
    private boolean owned(KnowledgeVectorCollection row,KnowledgeVectorCollection claimed,String owner,Date now) {
        return "RUNNING".equals(row.getCleanupState()) && Objects.equals(owner,row.getCleanupOwnerToken())
                && Objects.equals(row.getCleanupPlanKey(),claimed.getCleanupPlanKey()) && row.getCleanupLeaseUntil()!=null && row.getCleanupLeaseUntil().after(now)
                && Objects.equals(row.getRegistryId(),claimed.getRegistryId()) && Objects.equals(row.getOwnershipMarker(),claimed.getOwnershipMarker())
                && Objects.equals(row.getOwnershipVersion(),claimed.getOwnershipVersion()) && Objects.equals(row.getRegistryState(),claimed.getRegistryState())
                && Objects.equals(row.getCollectionName(),claimed.getCollectionName()) && Objects.equals(row.getBackendKey(),claimed.getBackendKey())
                && Objects.equals(row.getBackendType(),claimed.getBackendType()) && Objects.equals(row.getIndexVersion(),claimed.getIndexVersion())
                && Objects.equals(row.getCreationAcknowledged(),claimed.getCreationAcknowledged());
    }
    private boolean sameParent(AiKnowledgeBase a,AiKnowledgeBase b) {
        return Objects.equals(a.getIndexGeneration(),b.getIndexGeneration()) && Objects.equals(a.getVectorCollection(),b.getVectorCollection())
                && Objects.equals(a.getIndexVersion(),b.getIndexVersion()) && Objects.equals(a.getIndexStatus(),b.getIndexStatus()) && Objects.equals(a.getDelFlag(),b.getDelFlag());
    }
    private String planKey(AiKnowledgeBase parent,KnowledgeVectorCollection row,long expires) {
        StringBuilder data=new StringBuilder();
        for(Object field:new Object[]{parent.getId(),parent.getTenantId(),parent.getDelFlag(),parent.getIndexGeneration(),parent.getIndexVersion(),parent.getIndexStatus(),parent.getVectorCollection(),
                row.getRegistryId(),row.getBuildGeneration(),row.getIndexVersion(),row.getBackendKey(),row.getBackendType(),row.getCollectionName(),row.getOwnershipVersion(),row.getOwnershipMarker(),
                row.getRegistryState(),row.getCreationAcknowledged(),row.getRetireTime(),row.getUpdateTime(),row.getCleanupState(),row.getCleanupAttempts(),row.getCleanupAfter(),registry.cleanupRetentionMinutes(),expires}) {
            String value=field==null?null:field instanceof Date date?Long.toString(date.getTime()):field.toString();data.append(value==null?"N;":value.length()+":"+value+";");
        }
        return expires+"_"+KnowledgeDocumentSnapshotStore.digest(data.toString());
    }
    private View view(KnowledgeVectorCollection row,Date now) {
        return new View(row.getIndexVersion(),row.getBuildGeneration(),row.getCleanupState(),row.getCleanupAttempts(),row.getCleanupError(),row.getCleanupAfter(),
                "RUNNING".equals(row.getCleanupState()) && row.getCleanupLeaseUntil()!=null && !row.getCleanupLeaseUntil().after(now),row.getCleanupConfirmedAt());
    }
    private void validateGeneration(Long generation) {if(generation==null || generation<=0) throw new ServiceException("构建代次无效",400);}
    private ServiceException changed() {return new ServiceException("清理范围、计划或任务状态已变化，请重新查看；不会自动重复删除",409);}
    private record Claim(AiKnowledgeBase parent,KnowledgeVectorCollection row,boolean created) { }
    public record Plan(Long indexVersion,Long buildGeneration,String planId,Date expiresAt,boolean blocked,String reason) { }
    public record Request(String planId,Boolean confirmed,Boolean controlledStorageConfirmed) { }
    public record StopRequest(Boolean confirmed) { }
    public record View(Long indexVersion,Long buildGeneration,String state,int attempts,String errorCode,Date nextAttemptAt,boolean leaseExpired,Date confirmedAt) { }
}
