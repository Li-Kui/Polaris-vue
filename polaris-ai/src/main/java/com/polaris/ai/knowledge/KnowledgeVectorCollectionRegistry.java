package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.mapper.KnowledgeVectorCollectionMapper;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.common.exception.ServiceException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/** 新候选的持久登记；不扫描/认领历史集合，不包含远端删除或自动恢复。 */
@Service
public class KnowledgeVectorCollectionRegistry {
    private final KnowledgeVectorCollectionMapper collections;
    private final AiKnowledgeMapper knowledge;
    private final AiVectorStoreResolver resolver;
    private final TransactionTemplate writes;
    private final TransactionTemplate publication;
    private final Set<String> activeVerifications=new HashSet<>();
    private final Map<String,Long> recentVerifications=new HashMap<>();
    private int cleanupRetentionMinutes=1440;

    @Value("${ai.knowledge.vector-cleanup-retention-minutes:1440}")
    void configureCleanupRetention(int minutes) {
        if (minutes<1 || minutes>525600) throw new IllegalArgumentException("向量集合保留期必须为 1～525600 分钟");
        cleanupRetentionMinutes=minutes;
    }

    int cleanupRetentionMinutes() {return cleanupRetentionMinutes;}
    Date cleanupEligibleAfter(KnowledgeVectorCollection row) {
        Date retired="RETIRED".equals(row.getRegistryState())?row.getRetireTime():"ABANDONED".equals(row.getRegistryState())?row.getUpdateTime():null;
        long delay=cleanupRetentionMinutes*60_000L;
        return retired==null || retired.getTime()>Long.MAX_VALUE-delay?null:new Date(retired.getTime()+delay);
    }

    public KnowledgeVectorCollectionRegistry(KnowledgeVectorCollectionMapper collections, AiKnowledgeMapper knowledge,
            AiVectorStoreResolver resolver, PlatformTransactionManager manager) {
        this.collections=collections; this.knowledge=knowledge; this.resolver=resolver;
        this.writes=new TransactionTemplate(manager);
        // 远端请求之前元数据必须独立提交，不能被调用方长事务回滚成无记录载荷。
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.publication=new TransactionTemplate(manager);
    }

    public Handle reserve(KnowledgeIndexPublisher.Build build, long version, String name) {
        if (build == null || build.source() == null || build.source().getId() == null
                || name == null || !name.matches("[A-Za-z0-9_-]{1,255}")) throw changed();
        long previous=build.source().getIndexVersion()==null?0:build.source().getIndexVersion();
        if (previous==Long.MAX_VALUE || version!=previous+1 || build.generation()<=0) throw changed();
        AiKnowledgeBase source=new AiKnowledgeBase(); BeanUtils.copyProperties(build.source(),source);
        var target=resolver.storageIdentity();
        if (target==null || !List.of("QDRANT","MEMORY").contains(target.type())
                || target.key()==null || !target.key().matches("[a-f0-9]{64}")) throw changed();
        byte[] random=new byte[32]; new java.security.SecureRandom().nextBytes(random);
        String marker=java.util.HexFormat.of().formatHex(random);
        var handle=new Handle(source,build.generation(),version,UUID.randomUUID().toString(),target.key(),name,marker);
        writes.executeWithoutResult(status -> {
            requireCurrent(handle);
            var row=new KnowledgeVectorCollection();
            row.setRegistryId(handle.registryId()); row.setTenantId(source.getTenantId()); row.setKnowledgeBaseId(source.getId());
            row.setBuildGeneration(handle.generation()); row.setIndexVersion(version);
            row.setBackendType(target.type()); row.setBackendKey(target.key()); row.setCollectionName(name);
            row.setOwnershipVersion(1); row.setOwnershipMarker(marker);
            if (collections.insert(row)!=1) throw changed();
        });
        return handle;
    }

    public void creating(Handle handle) {advance(handle,"RESERVED","CREATING");}
    public void writing(Handle handle) {advance(handle,"CREATING","WRITING");}
    public void built(Handle handle) {advance(handle,"WRITING","BUILT");}

    private void advance(Handle handle,String expected,String next) {
        writes.executeWithoutResult(status -> {
            requireCurrent(handle);
            if (collections.advance(handle.source(),handle,expected,next)!=1) throw changed();
        });
    }

    /** 发布前的瞬时归属证据，远端读取在数据库事务之外进行。 */
    public PublicationProof publicationProof(KnowledgeIndexPublisher.Build build,AiKnowledgeBase candidate) {
        // 与发布事务分开：不得持父库/任务数据库锁等待远端网络。
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) throw changed();
        var row=collections.find(build.source(),build.generation());
        if (row==null || !"BUILT".equals(row.getRegistryState()) || !marked(row) || !Boolean.TRUE.equals(row.getCreationAcknowledged())
                || !Objects.equals(row.getCollectionName(),candidate.getVectorCollection())
                || !Objects.equals(row.getIndexVersion(),candidate.getIndexVersion()) || !sameRoute(row)) throw changed();
        var checked=resolver.verifyCollectionOwner(row.getCollectionName(),row.getOwnershipMarker());
        if (!matches(checked,row)) throw changed();
        return new PublicationProof(row.getRegistryId(),row.getOwnershipMarker(),System.nanoTime());
    }

    /** 与父库切换、任务成功加入同一事务；任何 CAS 失败会回滚登记和旧集合退役。 */
    public void publish(KnowledgeIndexPublisher.Build build,AiKnowledgeBase candidate,PublicationProof proof,Runnable activate) {
        publication.executeWithoutResult(status -> {
            String backend=resolver.storageIdentity().key();
            AiKnowledgeBase parent=collections.lockParent(build.source());
            if (!current(parent,build.source(),build.generation())) throw changed();
            var row=collections.candidate(build.source(),backend,candidate.getVectorCollection());
            if (row==null || !"BUILT".equals(row.getRegistryState()) || !Boolean.TRUE.equals(row.getCreationAcknowledged())
                    || !marked(row) || !sameRoute(row) || proof==null || !Objects.equals(row.getRegistryId(),proof.registryId())
                    || !Objects.equals(row.getOwnershipMarker(),proof.marker())
                    || System.nanoTime()-proof.checkedAt()<0 || System.nanoTime()-proof.checkedAt()>10_000_000_000L
                    || !Objects.equals(row.getBuildGeneration(),build.generation())
                    || !Objects.equals(row.getIndexVersion(),candidate.getIndexVersion())
                    || !Objects.equals(row.getTenantId(),build.source().getTenantId())) throw changed();
            var handle=new Handle(build.source(),build.generation(),row.getIndexVersion(),row.getRegistryId(),backend,row.getCollectionName(),row.getOwnershipMarker());
            activate.run();
            if (collections.advance(build.source(),handle,"BUILT","ACTIVE")!=1) throw changed();
            // 只退役同父库同路由下已登记的旧 ACTIVE，不根据旧指针/名称补造历史归属。
            if (build.source().getVectorCollection()!=null) collections.retirePrevious(build.source(),backend);
        });
    }

    public void abandon(Handle handle) {
        if (handle==null) return;
        writes.executeWithoutResult(status -> {
            collections.lockParent(handle.source());
            // 迟到失败不能覆盖 ACTIVE/RETIRED；远端请求可能已到达时只能记 UNKNOWN。
            collections.abandon(handle.source(),handle);
        });
    }

    public List<View> history(Long id) {
        var parent=accessible(id);
        return collections.history(parent).stream().map(r -> new View(r.getIndexVersion(),r.getBuildGeneration(),
                r.getRegistryState(),Boolean.TRUE.equals(r.getCreationAcknowledged()),r.getErrorCode(),r.getCreateTime(),r.getUpdateTime())).toList();
    }

    /** 本地条件只读预览，不生成删除授权/计划，不调用远端，不改变登记、任务或快照。 */
    public CleanupPreview cleanupPreview(Long id) {
        var source=accessible(id);
        return publication.execute(status -> {
            var parent=collections.lockParent(source);
            if (parent==null || !Objects.equals(source.getTenantId(),parent.getTenantId()))
                throw new ServiceException("集合预览范围已变化，请重新预览",409);
            Date now=collections.databaseNow();
            var rows=collections.cleanupPreview(parent);
            if (now==null || rows==null || rows.size()>21) throw new ServiceException("集合预览暂不可用",409);
            boolean blocked="BUILDING".equals(parent.getIndexStatus()) || collections.hasActiveWork(parent);
            var items=new java.util.ArrayList<CleanupItem>();
            for (var row:rows.stream().limit(20).toList()) {
                if (!Objects.equals(row.getTenantId(),parent.getTenantId()) || !Objects.equals(row.getKnowledgeBaseId(),parent.getId()))
                    throw new ServiceException("集合预览范围已变化，请重新预览",409);
                Date after=cleanupEligibleAfter(row);
                String reason=cleanupReason(parent,row,now,after,blocked);
                items.add(new CleanupItem(row.getIndexVersion(),row.getBuildGeneration(),row.getRegistryState(),after,reason));
            }
            int candidates=(int)items.stream().filter(item->"LOCAL_CANDIDATE".equals(item.reason())).count();
            return new CleanupPreview(now,cleanupRetentionMinutes/60.0,20,rows.size()>20,blocked,blocked?"ACTIVE_INDEX_TASK":null,candidates,List.copyOf(items));
        });
    }

    String cleanupReason(AiKnowledgeBase parent,KnowledgeVectorCollection row,Date now,Date after,boolean blocked) {
        if (row.getCleanupState()==null || row.getCleanupAttempts()==null) return "CLEANUP_SCHEMA_REQUIRED";
        if ("SUCCEEDED".equals(row.getCleanupState())) return "ALREADY_DELETED";
        if ("RUNNING".equals(row.getCleanupState())) return "CLEANUP_RUNNING";
        if ("MANUAL_REQUIRED".equals(row.getCleanupState())) return "MANUAL_REQUIRED";
        if (!List.of("NONE","RETRY_WAIT").contains(row.getCleanupState()) || row.getCleanupAttempts()<0 || row.getCleanupAttempts()>=5) return "CLEANUP_UNRESOLVED";
        if ("RETRY_WAIT".equals(row.getCleanupState()) && (row.getCleanupAfter()==null || row.getCleanupAfter().after(now))) return "RETRY_PENDING";
        return localCleanupReason(parent,row,now,after,blocked);
    }

    String localCleanupReason(AiKnowledgeBase parent,KnowledgeVectorCollection row,Date now,Date after,boolean blocked) {
        if (!List.of("RETIRED","ABANDONED").contains(Objects.toString(row.getRegistryState(),""))) return "STATE_PROTECTED";
        if (!marked(row)) return "LEGACY_UNVERIFIED";
        if (!Boolean.TRUE.equals(row.getCreationAcknowledged())) return "CREATE_UNCONFIRMED";
        if (!"QDRANT".equals(row.getBackendType())) return "BACKEND_UNSUPPORTED";
        if (!sameRoute(row)) return "ROUTE_CHANGED";
        String name=row.getCollectionName();
        if (name==null || !name.matches("[A-Za-z0-9_-]{1,255}") || row.getBuildGeneration()==null || row.getBuildGeneration()<=0
                || row.getIndexVersion()==null || row.getIndexVersion()<=0) return "DEFINITION_INVALID";
        if (collections.hasAnyReference(name)) return "ACTIVE_REFERENCE";
        if (after==null) return "RETIREMENT_UNKNOWN";
        if (after.after(now)) return "RETENTION_PENDING";
        if (blocked) return "ACTIVE_INDEX_TASK";
        if (collections.hasPreparedSnapshot(parent,KnowledgeDocumentSnapshotStore.digest(name))) return "SNAPSHOT_IN_PROGRESS";
        // 本地条件满足不等于可删除；执行入口仍需计划、确认与远端归属核验。
        return "LOCAL_CANDIDATE";
    }

    /** 显式诊断：远端只读、数据库不补写、不采用历史标记；MATCHED 不是删除授权。 */
    public Verification verifyOwnership(Long id,Long generation) {
        var parent=accessible(id);
        if (generation==null || generation<=0) throw new ServiceException("构建代次无效",400);
        var row=collections.find(parent,generation);
        if (row==null) throw new ServiceException("集合登记不存在或无权访问",404);
        if (List.of("RUNNING","SUCCEEDED").contains(Objects.toString(row.getCleanupState(),""))) return verification(row,"UNRESOLVED");
        if (!marked(row)) return verification(row,"LEGACY_UNVERIFIED");
        if (!List.of("ACTIVE","RETIRED","BUILT","ABANDONED").contains(row.getRegistryState()) || !Boolean.TRUE.equals(row.getCreationAcknowledged()))
            return verification(row,"UNRESOLVED");
        if (!sameRoute(row)) return verification(row,"ROUTE_CHANGED");
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new ServiceException("归属核验不可在数据库事务中执行",409);
        var caller=CallerContextHolder.require();String key=Objects.toString(caller.getTenantId(),"system")+":"+caller.getUserId();
        beginVerification(key);
        try {
            var result=resolver.verifyCollectionOwner(row.getCollectionName(),row.getOwnershipMarker());
            var after=accessible(id);var latest=collections.find(after,generation);
            if (latest==null || !Objects.equals(row.getRegistryId(),latest.getRegistryId())
                    || !Objects.equals(row.getOwnershipMarker(),latest.getOwnershipMarker())
                    || !Objects.equals(row.getOwnershipVersion(),latest.getOwnershipVersion())
                    || !Objects.equals(row.getBackendKey(),latest.getBackendKey())
                    || !Objects.equals(row.getBackendType(),latest.getBackendType())
                    || !Objects.equals(row.getCollectionName(),latest.getCollectionName())
                    || !Objects.equals(row.getIndexVersion(),latest.getIndexVersion())
                    || !Objects.equals(row.getCreationAcknowledged(),latest.getCreationAcknowledged())
                    || !Objects.equals(row.getRegistryState(),latest.getRegistryState())
                    || !Objects.equals(parent.getIndexGeneration(),after.getIndexGeneration())
                    || !Objects.equals(parent.getIndexVersion(),after.getIndexVersion())
                    || !Objects.equals(parent.getIndexStatus(),after.getIndexStatus())
                    || !Objects.equals(parent.getVectorCollection(),after.getVectorCollection())
                    || !Objects.equals(parent.getDelFlag(),after.getDelFlag())) return verification(row,"CHANGED");
            return verification(row,result==null?"UNAVAILABLE":result.name());
        } finally {endVerification(key);}
    }

    AiKnowledgeBase accessible(Long id) {
        var caller=CallerContextHolder.get();
        if (caller==null || caller.getUserId()==null || caller.getUserId()<=0
                || !(caller.isSuperAdmin() || caller.isPlatformMode() && caller.isTenantAdmin()))
            throw new ServiceException("仅系统超级管理员或当前租户管理员可查看集合登记",403);
        if (id==null || id<=0) throw new ServiceException("知识库标识无效",400);
        var query=new AiKnowledgeBase(); query.setId(id);
        var parent=knowledge.selectAccessibleCleanupParent(query);
        if (parent==null || !caller.isSuperAdmin() && !Objects.equals(caller.getTenantId(),Objects.toString(parent.getTenantId(),null)))
            throw new ServiceException("知识库不存在或无权访问",404);
        return parent;
    }

    private boolean marked(KnowledgeVectorCollection row) {
        return Objects.equals(row.getOwnershipVersion(),1) && row.getOwnershipMarker()!=null && row.getOwnershipMarker().matches("[a-f0-9]{64}");
    }
    private boolean sameRoute(KnowledgeVectorCollection row) {
        var route=resolver.storageIdentity();return route!=null && Objects.equals(route.key(),row.getBackendKey()) && Objects.equals(route.type(),row.getBackendType());
    }
    private boolean matches(AiVectorStoreResolver.OwnershipCheck result,KnowledgeVectorCollection row) {
        return "QDRANT".equals(row.getBackendType()) && result==AiVectorStoreResolver.OwnershipCheck.MATCHED
                || "MEMORY".equals(row.getBackendType()) && result==AiVectorStoreResolver.OwnershipCheck.LOCAL_MATCHED;
    }
    private Verification verification(KnowledgeVectorCollection row,String result) {return new Verification(row.getIndexVersion(),row.getBuildGeneration(),result);}
    private synchronized void beginVerification(String key) {
        long now=System.nanoTime();recentVerifications.entrySet().removeIf(e->now-e.getValue()>=5_000_000_000L);
        if (activeVerifications.contains(key) || activeVerifications.size()>=8 || recentVerifications.containsKey(key) || recentVerifications.size()>=4096)
            throw new ServiceException("归属核验正在进行或请求过于频繁，请稍后重试",429);
        activeVerifications.add(key);recentVerifications.put(key,now);
    }
    private synchronized void endVerification(String key) {activeVerifications.remove(key);}

    private void requireCurrent(Handle handle) {
        if (handle==null || !Objects.equals(resolver.storageIdentity().key(),handle.backendKey())
                || !current(collections.lockParent(handle.source()),handle.source(),handle.generation())) throw changed();
    }
    private boolean current(AiKnowledgeBase parent,AiKnowledgeBase source,long generation) {
        return parent!=null && "0".equals(parent.getDelFlag()) && "BUILDING".equals(parent.getIndexStatus())
                && Objects.equals(parent.getTenantId(),source.getTenantId()) && Objects.equals(parent.getIndexGeneration(),generation)
                && Objects.equals(parent.getIndexVersion(),source.getIndexVersion()) && Objects.equals(parent.getVectorCollection(),source.getVectorCollection());
    }
    private IllegalStateException changed() {return new IllegalStateException("候选集合登记或构建来源已变化，停止旧构建；不会自动接管或清理");}
    public record Handle(AiKnowledgeBase source,long generation,long version,String registryId,String backendKey,String collectionName,String ownershipMarker) {
        @Override public String toString() {return "KnowledgeCollectionHandle[redacted]";}
    }
    public record PublicationProof(String registryId,String marker,long checkedAt) {
        @Override public String toString() {return "KnowledgeCollectionProof[redacted]";}
    }
    public record Verification(Long indexVersion,Long buildGeneration,String state) { }
    public record View(Long indexVersion,Long buildGeneration,String state,boolean creationAcknowledged,String errorCode,Date createTime,Date updateTime) { }
    public record CleanupItem(Long indexVersion,Long buildGeneration,String state,Date eligibleAfter,String reason) { }
    public record CleanupPreview(Date observedAt,double retentionHours,int batchLimit,boolean truncated,boolean blocked,String reason,int localCandidateCount,List<CleanupItem> items) { }
}
