package com.polaris.ai.knowledge;

import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.mapper.KnowledgeIndexBuildMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;

/** 仅负责候选发布保护；不冒充持久化任务、租约或向量库回读校验。 */
@Service
public class KnowledgeIndexPublisher {
    private final AiKnowledgeMapper knowledge;
    private final AiDocumentMapper documents;
    private final KnowledgeIndexBuildMapper builds;
    private final KnowledgeDocumentSnapshotStore snapshots;
    private final KnowledgeRebuildTaskFence taskFence;
    private final KnowledgeVectorCollectionRegistry collections;

    @org.springframework.beans.factory.annotation.Autowired
    public KnowledgeIndexPublisher(AiKnowledgeMapper knowledge, AiDocumentMapper documents,
                                   KnowledgeIndexBuildMapper builds, KnowledgeDocumentSnapshotStore snapshots,
                                   KnowledgeRebuildTaskFence taskFence, KnowledgeVectorCollectionRegistry collections) {
        this.knowledge = knowledge; this.documents = documents; this.builds = builds; this.snapshots = snapshots;
        this.taskFence = taskFence;
        this.collections = collections;
    }

    public Build begin(AiKnowledgeBase persisted) {
        AiKnowledgeBase source = new AiKnowledgeBase();
        BeanUtils.copyProperties(persisted, source);
        long generation = Objects.requireNonNull(source.getIndexGeneration(), "索引代次缺失，请先执行 SQL 22");
        if (generation == Long.MAX_VALUE || builds.begin(source, generation) != 1) throw changed();
        return new Build(source, generation + 1);
    }

    public List<Entry> manifest(List<AiDocument> rows) {
        if (rows == null || rows.size() > 1000) throw new IllegalStateException("本批重建最多支持 1000 份文档");
        Set<Long> ids = new HashSet<>();
        List<Entry> entries = new ArrayList<>();
        for (AiDocument doc : rows) {
            if (doc == null || doc.getId() == null || !ids.add(doc.getId())) throw changed();
            entries.add(new Entry(doc.getId(), doc.getKnowledgeBaseId(), doc.getName(), doc.getFileUrl()));
        }
        return List.copyOf(entries);
    }

    public void requireCurrent(Build build) {
        if (taskFence != null) taskFence.check();
        if (!current(build)) throw changed();
    }

    public boolean current(Build build) {
        AiKnowledgeBase row = knowledge.selectWorkflowResource(build.source().getTenantId(), build.source().getId());
        return row != null && Objects.equals(row.getTenantId(), build.source().getTenantId())
                && Objects.equals(row.getIndexGeneration(), build.generation())
                && Objects.equals(row.getIndexVersion(), build.source().getIndexVersion())
                && Objects.equals(row.getVectorCollection(), build.source().getVectorCollection())
                && "BUILDING".equals(row.getIndexStatus());
    }

    /** 增量上传仍写当前活动 collection，配置/指针变化后停止继续提交批次。 */
    public void requireImportCurrent(AiKnowledgeBase source) {
        if (taskFence != null) taskFence.check();
        AiKnowledgeBase row = knowledge.selectWorkflowResource(source.getTenantId(), source.getId());
        if (row == null || !Objects.equals(row.getTenantId(), source.getTenantId())
                || !Objects.equals(row.getIndexGeneration(), source.getIndexGeneration())
                || !Objects.equals(row.getIndexVersion(), source.getIndexVersion())
                || !Objects.equals(row.getVectorCollection(), source.getVectorCollection())
                || !"READY".equals(row.getIndexStatus())) throw changed();
    }

    public void activate(Build build, AiKnowledgeBase candidate, List<Entry> manifest) {
        requireCurrent(build);
        AiDocument query = new AiDocument(); query.setKnowledgeBaseId(build.source().getId());
        List<AiDocument> current = documents.selectDocumentList(query);
        if (current == null || current.size() != manifest.size()) throw changed();
        Map<Long, AiDocument> byId = new HashMap<>();
        for (AiDocument row : current) if (byId.put(row.getId(), row) != null) throw changed();
        List<Long> excluded = new ArrayList<>();
        int ready = 0;
        for (Entry entry : manifest) {
            AiDocument row = byId.get(entry.id());
            if (row == null || !Objects.equals(row.getKnowledgeBaseId(), build.source().getId())
                    || !Objects.equals(entry.knowledgeId(), row.getKnowledgeBaseId())
                    || !Objects.equals(entry.name(), row.getName()) || !Objects.equals(entry.fileUrl(), row.getFileUrl())
                    || !"0".equals(row.getDelFlag())) throw changed();
            if (excluded(row)) {excluded.add(row.getId()); continue;}
            if (!"2".equals(row.getStatus()) || !"SAFE".equals(row.getModerationStatus())) throw changed();
            KnowledgeDocumentSnapshot snapshot = snapshots.latest(candidate, row, true);
            if (snapshot == null || !"READY".equals(snapshot.getSnapshotState())
                    || snapshot.getChunkCount() == null || snapshot.getChunkCount() < 1
                    || !snapshots.sourceMatches(row, snapshot)) throw changed();
            // 同时核验私有载荷摘要和块数；不是向量库的独立回读。
            snapshots.load(snapshot);
            ready++;
        }
        candidate.setIndexStatus(ready == 0 ? "EMPTY" : "READY");
        Runnable publish = () -> {
            if (builds.activate(build.source(), build.generation(), candidate,
                    manifest.stream().map(Entry::id).toList(), excluded) != 1) throw changed();
        };
        var proof = collections.publicationProof(build,candidate);
        Runnable registeredPublish = () -> collections.publish(build,candidate,proof,publish);
        if (taskFence == null) registeredPublish.run(); else taskFence.publish(registeredPublish);
    }

    public void taskManifest(List<Entry> manifest) {if (taskFence != null) taskFence.manifest(manifest);}
    public void taskDocumentCompleted() {if (taskFence != null) taskFence.completed();}
    public void completeImport(Runnable action) {if (taskFence == null) action.run(); else taskFence.completeImport(action);}

    public void failed(Build build) {
        builds.fail(build.source(), build.generation());
    }

    public void documentChanged(AiKnowledgeBase parent) {
        if (parent == null || builds.documentChanged(parent) != 1) throw changed();
    }

    public static boolean excluded(AiDocument row) {
        return "QUARANTINED".equals(row.getModerationStatus()) || "AUTO_DELETED".equals(row.getModerationStatus());
    }

    private IllegalStateException changed() {
        return new IllegalStateException("索引构建来源已变化，旧候选未发布，请刷新后显式重建");
    }

    public record Build(AiKnowledgeBase source, long generation) { }
    public record Entry(Long id, Long knowledgeId, String name, String fileUrl) { }
}
