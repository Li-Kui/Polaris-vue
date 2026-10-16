package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.mapper.KnowledgeSnapshotCleanupMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/** 显式、限量的本机快照清理；不扫描目录，不删除向量，不调用模型，不自动恢复活动任务。 */
@Service
public class KnowledgeSnapshotCleanupService {
    private final AiKnowledgeMapper knowledge;
    private final KnowledgeSnapshotCleanupMapper cleanup;
    private final KnowledgeDocumentSnapshotStore files;
    private final TransactionTemplate transactions;

    public KnowledgeSnapshotCleanupService(AiKnowledgeMapper knowledge, KnowledgeSnapshotCleanupMapper cleanup,
            KnowledgeDocumentSnapshotStore files, PlatformTransactionManager manager) {
        this.knowledge = knowledge; this.cleanup = cleanup; this.files = files; this.transactions = new TransactionTemplate(manager);
    }
    public Preview preview(Long id) {
        AiKnowledgeBase source = accessible(id);
        return transactions.execute(status -> {
            AiKnowledgeBase parent = parent(source);
            if (blocked(parent)) return new Preview(null, null, 0, true, "ACTIVE_INDEX_TASK", 20);
            List<KnowledgeDocumentSnapshot> rows = candidates(parent);
            long expires = cleanup.databaseNow().getTime() + 300_000L;
            return new Preview(plan(parent, rows, expires), new Date(expires), rows.size(), false, null, 20);
        });
    }
    /** 独立只读状态，不领取任务、不核验文件存在、不使记录提前到期。 */
    public StatusPage status(Long id,Long beforeId) {
        return statusPage(accessible(id),beforeId);
    }
    public StatusPage statusFromRequest(Long id,String beforeId) {
        var source=accessible(id);Long cursor=null;
        try {if (beforeId!=null) cursor=Long.valueOf(beforeId);}
        catch (NumberFormatException e) {throw new ServiceException("清理记录游标无效",400);}
        return statusPage(source,cursor);
    }
    private StatusPage statusPage(AiKnowledgeBase source,Long beforeId) {
        if (beforeId!=null && beforeId<=0) throw new ServiceException("清理记录游标无效",400);
        return transactions.execute(status -> {
            var parent=parent(source);var rows=cleanup.statusRows(parent,beforeId);
            if (rows==null || rows.size()>21) throw changed();
            var items=new java.util.ArrayList<StatusItem>();
            for(var row:rows.stream().limit(20).toList()) {
                if (!Objects.equals(row.getTenantId(),parent.getTenantId()) || !Objects.equals(row.getKnowledgeBaseId(),parent.getId())) throw changed();
                Date after=row.getPayloadCleanupAfter();
                if ("NONE".equals(row.getPayloadCleanupState()) && row.getCreateTime()!=null && row.getCreateTime().getTime()<=Long.MAX_VALUE-86_400_000L)
                    after=new Date(row.getCreateTime().getTime()+86_400_000L);
                boolean protectedActive="0".equals(parent.getDelFlag()) && parent.getVectorCollection()!=null
                        && Objects.equals(row.getCollectionHash(),KnowledgeDocumentSnapshotStore.digest(parent.getVectorCollection()));
                items.add(new StatusItem(row.getId(),row.getDocumentId(),row.getIndexVersion(),row.getSnapshotState(),row.getPayloadCleanupState(),
                        row.getPayloadCleanupAttempts(),row.getPayloadCleanupError(),after,protectedActive));
            }
            boolean more=rows.size()>20;
            return new StatusPage(cleanup.databaseNow(),List.copyOf(items),more,more?items.get(items.size()-1).snapshotId():null);
        });
    }
    public Result execute(Long id, Request request) {
        AiKnowledgeBase source = accessible(id);
        if (request == null || !Boolean.TRUE.equals(request.confirmed()) || request.planId() == null
                || !request.planId().matches("[0-9]{13}_[a-f0-9]{64}"))
            throw new ServiceException("请先预览并明确确认清理，操作不会自动执行", 400);
        long expires = Long.parseLong(request.planId().substring(0, 13));
        return transactions.execute(status -> {
            AiKnowledgeBase parent = parent(source);
            long now = cleanup.databaseNow().getTime();
            if (expires <= now || expires > now + 300_000L || blocked(parent)) throw changed();
            List<KnowledgeDocumentSnapshot> rows = candidates(parent);
            if (!plan(parent, rows, expires).equals(request.planId())) throw changed();
            int done=0, retry=0, manual=0;
            for (KnowledgeDocumentSnapshot row : rows) {
                // FAILED/RETIRED 不会恢复为 READY；行锁内只操作服务端 UUID 对应的一个载荷。
                var result = files.cleanupPayload(row.getSnapshotKey());
                String state = result == KnowledgeDocumentSnapshotStore.PayloadDeletion.DONE ? "SUCCEEDED"
                        : result == KnowledgeDocumentSnapshotStore.PayloadDeletion.INVALID_PATH || row.getPayloadCleanupAttempts() >= 4
                        ? "MANUAL_REQUIRED" : "RETRY_WAIT";
                String error = result == KnowledgeDocumentSnapshotStore.PayloadDeletion.DONE ? null
                        : result == KnowledgeDocumentSnapshotStore.PayloadDeletion.INVALID_PATH ? "PAYLOAD_PATH_INVALID" : "PAYLOAD_DELETE_FAILED";
                if (cleanup.finish(parent, row, state, error) != 1) throw changed();
                if ("SUCCEEDED".equals(state)) done++; else if ("RETRY_WAIT".equals(state)) retry++; else manual++;
            }
            return new Result(rows.size(), done, retry, manual);
        });
    }
    private AiKnowledgeBase accessible(Long id) {
        var caller=CallerContextHolder.get();
        if (caller == null || caller.getUserId() == null || caller.getUserId() <= 0
                || !(caller.isSuperAdmin() || caller.isPlatformMode() && caller.isTenantAdmin()))
            throw new ServiceException("仅系统超级管理员或当前租户管理员可管理快照清理",403);
        if (id == null || id <= 0) throw new ServiceException("知识库标识无效",400);
        var query=new AiKnowledgeBase();query.setId(id);
        AiKnowledgeBase parent=knowledge.selectAccessibleCleanupParent(query);
        if (parent == null || !caller.isSuperAdmin() && !Objects.equals(caller.getTenantId(),Objects.toString(parent.getTenantId(),null)))
            throw new ServiceException("知识库不存在或无权访问",404);
        return parent;
    }
    private AiKnowledgeBase parent(AiKnowledgeBase source) {
        AiKnowledgeBase parent=cleanup.lockParent(source);
        if (parent == null || !Objects.equals(source.getTenantId(),parent.getTenantId())) throw changed();
        return parent;
    }
    private boolean blocked(AiKnowledgeBase source) {
        // 包含过期但未显式处理的任务/owner，不以清理操作偷偷接管未知结果。
        return "BUILDING".equals(source.getIndexStatus()) || cleanup.activeTasks(source)>0 || cleanup.documentOwners(source)>0;
    }
    private List<KnowledgeDocumentSnapshot> candidates(AiKnowledgeBase parent) {
        List<KnowledgeDocumentSnapshot> rows=cleanup.eligible(parent,KnowledgeDocumentSnapshotStore.digest(parent.getVectorCollection()));
        if (rows == null || rows.size()>20) throw changed();
        for (KnowledgeDocumentSnapshot row:rows) {
            if (!Objects.equals(row.getTenantId(),parent.getTenantId()) || !Objects.equals(row.getKnowledgeBaseId(),parent.getId())
                    || !("FAILED".equals(row.getSnapshotState()) || "RETIRED".equals(row.getSnapshotState()))
                    || "0".equals(parent.getDelFlag()) && parent.getVectorCollection()!=null
                        && Objects.equals(row.getCollectionHash(),KnowledgeDocumentSnapshotStore.digest(parent.getVectorCollection()))) throw changed();
        }
        return rows;
    }
    private String plan(AiKnowledgeBase parent,List<KnowledgeDocumentSnapshot> rows,long expires) {
        StringBuilder data=new StringBuilder();
        for (Object field:new Object[]{parent.getId(),parent.getTenantId(),parent.getDelFlag(),parent.getIndexGeneration(),parent.getIndexVersion(),parent.getVectorCollection(),expires}) append(data,field);
        for (KnowledgeDocumentSnapshot row:rows)
            for (Object field:new Object[]{row.getId(),row.getSnapshotKey(),row.getCollectionHash(),row.getSnapshotState(),row.getPayloadHash(),row.getPayloadCleanupState(),row.getPayloadCleanupAttempts(),row.getPayloadCleanupAfter()}) append(data,field);
        // 摘要不是授权凭证；执行必须重新鉴权、锁父库并重查同一批候选。
        return expires+"_"+KnowledgeDocumentSnapshotStore.digest(data.toString());
    }
    private void append(StringBuilder target,Object field) {
        String value=field == null ? null : field instanceof Date date ? Long.toString(date.getTime()) : field.toString();
        target.append(value == null ? "N;" : value.length()+":"+value+";");
    }
    private ServiceException changed() {return new ServiceException("清理范围已变化、计划已过期或存在活动任务，请重新预览",409);}
    public record Preview(String planId,Date expiresAt,int eligibleCount,boolean blocked,String reason,int batchLimit) { }
    public record Request(String planId,Boolean confirmed) { }
    public record Result(int attempted,int cleaned,int retryWaiting,int manualRequired) { }
    public record StatusItem(Long snapshotId,Long documentId,Long indexVersion,String snapshotState,String cleanupState,Integer attempts,String errorCode,Date nextAttemptAt,boolean activeProtected) { }
    public record StatusPage(Date observedAt,List<StatusItem> items,boolean hasMore,Long nextBeforeId) { }
}
