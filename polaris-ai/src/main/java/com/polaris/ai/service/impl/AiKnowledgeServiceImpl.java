package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.knowledge.KnowledgeDocumentParser;
import com.polaris.ai.knowledge.KnowledgeDocumentSnapshot;
import com.polaris.ai.knowledge.KnowledgeDocumentSnapshotStore;
import com.polaris.ai.knowledge.KnowledgeIndexPublisher;
import com.polaris.ai.rag.AiVectorStoreProperties;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.ai.runtime.embedding.EmbeddingRuntimeDescriptor;
import com.polaris.ai.service.IAiKnowledgeService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * AI 知识库服务层实现类
 * 
 * @author polaris
 */
@Slf4j
@Service
public class AiKnowledgeServiceImpl extends ServiceImpl<AiKnowledgeMapper, AiKnowledgeBase> implements IAiKnowledgeService
{
    private static final int DEFAULT_CHUNK_SIZE = 300;
    private static final int DEFAULT_CHUNK_OVERLAP = 30;
    private static final int DEFAULT_RETRIEVAL_TOP_K = 5;
    private static final double DEFAULT_RETRIEVAL_MIN_SCORE = 0.5;
    private static final Set<Long> ACTIVE_REBUILDS = ConcurrentHashMap.newKeySet();

    @Autowired
    private AiKnowledgeMapper aiKnowledgeMapper;

    @Autowired
    private AiDocumentMapper aiDocumentMapper;

    @Autowired
    private AiVectorStoreResolver vectorStoreResolver;

    @Autowired
    private AiVectorStoreProperties vectorStoreProperties;

    @Autowired
    private com.polaris.ai.safety.service.IModerationFacade moderationFacade;

    @Autowired
    private com.polaris.ai.safety.guard.KnowledgeModerationGuard knowledgeModerationGuard;

    @Autowired
    private KnowledgeDocumentParser documentParser;

    @Autowired
    private KnowledgeDocumentSnapshotStore documentSnapshots;

    @Autowired
    private KnowledgeIndexPublisher indexPublisher;

    @Autowired
    private com.polaris.ai.knowledge.KnowledgeVectorCollectionRegistry vectorCollections;

    @Autowired
    private com.polaris.ai.knowledge.KnowledgeDocumentWorkerFence documentWorker;

    @Autowired
    private com.polaris.ai.knowledge.KnowledgeRebuildTaskService rebuildTasks;

    // ================================================================
    //  知识库 CRUD
    // ================================================================

    @Override
    public List<AiKnowledgeBase> listKnowledgeBase(AiKnowledgeBase kb)
    {
        return aiKnowledgeMapper.selectKnowledgeBaseList(kb);
    }

    @Override
    public AiKnowledgeBase selectKnowledgeBaseById(Long id)
    {
        return aiKnowledgeMapper.selectById(id);
    }

    @Override
    public AiKnowledgeBase selectAccessibleKnowledgeBaseById(Long id)
    {
        if (id == null) {
            return null;
        }
        AiKnowledgeBase query = new AiKnowledgeBase();
        query.setId(id);
        return aiKnowledgeMapper.selectAccessibleKnowledgeBaseById(query);
    }

    @Override
    public int insertKnowledgeBase(AiKnowledgeBase kb)
    {
        applyDefaultsAndValidate(kb);
        EmbeddingRuntimeDescriptor descriptor = vectorStoreResolver.prepare(kb);
        validateEmbeddingModelScope(kb, descriptor);
        // 新库不得采用外部传入的活动集合；只有创建新候选并通过发布 CAS 才能绑定。
        kb.setVectorCollection(null);
        kb.setIndexSignature(null);
        kb.setIndexGeneration(0L);
        kb.setIndexVersion(0L);
        kb.setIndexStatus("EMPTY");
        kb.setIndexError(null);
        return aiKnowledgeMapper.insert(kb);
    }

    @Override
    public int updateKnowledgeBase(AiKnowledgeBase kb)
    {
        AiKnowledgeBase existing = aiKnowledgeMapper.selectById(kb.getId());
        if (existing == null) {
            return 0;
        }
        mergeIndexDefaults(kb, existing);
        applyDefaultsAndValidate(kb);
        EmbeddingRuntimeDescriptor descriptor = vectorStoreResolver.prepare(kb);
        validateEmbeddingModelScope(kb, descriptor);
        boolean indexConfigChanged = !Objects.equals(existing.getEmbeddingModelId(), kb.getEmbeddingModelId())
                || !Objects.equals(existing.getEmbeddingDimension(), kb.getEmbeddingDimension())
                || !Objects.equals(existing.getEmbeddingModelRevision(), kb.getEmbeddingModelRevision())
                || !Objects.equals(existing.getEmbeddingSchemaHash(), kb.getEmbeddingSchemaHash())
                || !Objects.equals(existing.getChunkSize(), kb.getChunkSize())
                || !Objects.equals(existing.getChunkOverlap(), kb.getChunkOverlap())
                || !Objects.equals(existing.getSplitterType(), kb.getSplitterType());
        if (indexConfigChanged) {
            kb.setIndexStatus("STALE");
            kb.setIndexError(null);
        }
        // 外部提交的活动指针、状态、签名与代次一律不写回，避免编辑表单覆盖后台发布。
        AiKnowledgeBase changes = new AiKnowledgeBase();
        changes.setName(kb.getName()); changes.setDescription(kb.getDescription());
        changes.setDeptId(existing.getDeptId()); changes.setUpdateBy(kb.getUpdateBy());
        changes.setEmbeddingModelId(kb.getEmbeddingModelId()); changes.setEmbeddingDimension(kb.getEmbeddingDimension());
        changes.setEmbeddingModelRevision(kb.getEmbeddingModelRevision()); changes.setEmbeddingSchemaHash(kb.getEmbeddingSchemaHash());
        changes.setChunkSize(kb.getChunkSize()); changes.setChunkOverlap(kb.getChunkOverlap()); changes.setSplitterType(kb.getSplitterType());
        changes.setRetrievalTopK(kb.getRetrievalTopK()); changes.setRetrievalMinScore(kb.getRetrievalMinScore());
        LambdaUpdateWrapper<AiKnowledgeBase> update = new LambdaUpdateWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getId, existing.getId())
                .eq(AiKnowledgeBase::getIndexGeneration, Objects.requireNonNull(existing.getIndexGeneration(), "请先执行 SQL 22"))
                .setSql("index_generation = index_generation + 1");
        if (indexConfigChanged || "BUILDING".equals(existing.getIndexStatus())) {
            update.set(AiKnowledgeBase::getIndexStatus, "STALE")
                    .set(AiKnowledgeBase::getIndexError, "索引配置已变更，请重建知识库索引");
        }
        int result = aiKnowledgeMapper.update(changes, update);
        if (result != 1) throw new IllegalStateException("知识库已变化，请刷新后重试");
        return result;
    }

    @Override
    public int markIndexesStaleByEmbeddingModelId(Long modelConfigId, String reason)
    {
        if (modelConfigId == null) {
            return 0;
        }
        return aiKnowledgeMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getEmbeddingModelId, modelConfigId)
                .setSql("index_generation = index_generation + 1")
                .set(AiKnowledgeBase::getIndexStatus, "STALE")
                .set(AiKnowledgeBase::getIndexError, abbreviate(reason, 1000)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBase(Long id)
    {
        AiKnowledgeBase knowledgeBase = aiKnowledgeMapper.selectById(id);
        if (knowledgeBase != null) indexPublisher.documentChanged(knowledgeBase);
        // 1. 删除向量数据库中该知识库下的所有向量分片
        if (knowledgeBase != null && knowledgeBase.getVectorCollection() != null) {
            try {
                Filter filter = metadataKey("knowledge_base_id").isEqualTo(id.toString());
                vectorStoreResolver.resolveStoreForRemoval(knowledgeBase).removeAll(filter);
            } catch (Exception e) {
                log.warn("从向量数据库移除知识库 {} 的数据失败", id, e);
            }
        }

        // 2. 级联逻辑删除数据库中的文档列表与知识库本身
        aiDocumentMapper.delete(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKnowledgeBaseId, id));
        int changed = aiKnowledgeMapper.deleteById(id);
        if (changed > 0) documentSnapshots.retire(id, null);
        return changed;
    }

    // ================================================================
    //  文档 CRUD
    // ================================================================

    @Override
    public List<AiDocument> listDocument(AiDocument doc)
    {
        return aiDocumentMapper.selectDocumentList(doc);
    }

    @Override
    public AiDocument selectDocumentById(Long id)
    {
        return aiDocumentMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDocument(AiDocument doc)
    {
        // 与插入同一事务；先锁父库代次，使候选切换不可能漏掉刚提交的资料。
        indexPublisher.documentChanged(aiKnowledgeMapper.selectById(doc.getKnowledgeBaseId()));
        doc.setStatus("0"); // 待解析
        doc.setWordCount(0);
        int changed = aiDocumentMapper.insert(doc);
        if (changed != 1) throw new IllegalStateException("文档保存失败");
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDocument(Long id)
    {
        AiDocument document = aiDocumentMapper.selectById(id);
        // 1. 从向量库清除该文档的向量
        if (document != null) {
            AiKnowledgeBase knowledgeBase = aiKnowledgeMapper.selectById(document.getKnowledgeBaseId());
            if (knowledgeBase != null) indexPublisher.documentChanged(knowledgeBase);
            if (knowledgeBase != null && knowledgeBase.getVectorCollection() != null) {
                try {
                    Filter filter = metadataKey("document_id").isEqualTo(id.toString());
                    vectorStoreResolver.resolveStoreForRemoval(knowledgeBase).removeAll(filter);
                } catch (Exception e) {
                    log.warn("从向量数据库移除文档 {} 的数据失败", id, e);
                }
            }
        }

        // 2. 逻辑删除数据库记录
        int changed = aiDocumentMapper.deleteById(id);
        if (changed > 0 && document != null) documentSnapshots.retire(document.getKnowledgeBaseId(), id);
        return changed;
    }

    // ================================================================
    //  RAG 核心：切片与向量化
    // ================================================================

    @Override
    public void runKnowledgeImportTask(AiKnowledgeBase source, Long documentId)
    {
        AiKnowledgeBase frozen = new AiKnowledgeBase(); org.springframework.beans.BeanUtils.copyProperties(source, frozen);
        AiDocument doc = aiDocumentMapper.selectById(documentId);
        if (doc == null || !Objects.equals(doc.getKnowledgeBaseId(), frozen.getId())
                || !"0".equals(doc.getStatus()) || KnowledgeIndexPublisher.excluded(doc))
            throw new IllegalStateException("待导入文档已变化，请显式重建");
        try {
            applyDefaultsAndValidate(frozen);
            indexPublisher.requireImportCurrent(frozen);
            indexPublisher.taskManifest(indexPublisher.manifest(List.of(doc)));
            AiVectorStoreResolver.VectorContext context = vectorStoreResolver.resolve(frozen);
            indexPublisher.requireImportCurrent(frozen);
            indexDocument(doc, frozen, frozen.getIndexVersion(), context, () -> indexPublisher.requireImportCurrent(frozen));
        } catch (RuntimeException e) {
            documentWorker.failPending(frozen, doc);
            throw e;
        }
    }

    @Override
    public void rebuildKnowledgeBaseAsync(Long knowledgeBaseId)
    {
        rebuildTasks.submit(knowledgeBaseId, null, null);
    }

    private boolean rebuildKnowledgeBase(Long knowledgeBaseId)
    {
        return rebuildKnowledgeBase(knowledgeBaseId, null);
    }

    @Override
    public void runKnowledgeRebuildTask(KnowledgeIndexPublisher.Build build)
    {
        rebuildKnowledgeBase(build.source().getId(), build);
    }

    private boolean rebuildKnowledgeBase(Long knowledgeBaseId, KnowledgeIndexPublisher.Build reserved)
    {
        if (knowledgeBaseId == null || !ACTIVE_REBUILDS.add(knowledgeBaseId)) {
            log.info(">>> 知识库 {} 已有重建任务运行，忽略重复请求", knowledgeBaseId);
            return false;
        }
        AiKnowledgeBase knowledgeBase = reserved == null ? aiKnowledgeMapper.selectById(knowledgeBaseId) : new AiKnowledgeBase();
        if (reserved != null) org.springframework.beans.BeanUtils.copyProperties(reserved.source(), knowledgeBase);
        if (knowledgeBase == null) {
            ACTIVE_REBUILDS.remove(knowledgeBaseId);
            return true;
        }

        KnowledgeIndexPublisher.Build build = null;
        com.polaris.ai.knowledge.KnowledgeVectorCollectionRegistry.Handle candidateHandle = null;
        try {
            // 先保存发布代次再做外部调用；跨实例旧 worker 无法覆盖新候选或新配置。
            build = reserved == null ? indexPublisher.begin(knowledgeBase) : reserved;
            knowledgeBase.setIndexGeneration(build.generation());
            KnowledgeIndexPublisher.Build claimed = build;
            applyDefaultsAndValidate(knowledgeBase);
            EmbeddingRuntimeDescriptor descriptor =
                    vectorStoreResolver.prepare(knowledgeBase);
            long nextVersion = knowledgeBase.getIndexVersion() == null
                    ? 1L : knowledgeBase.getIndexVersion() + 1L;
            String nextCollection = vectorStoreResolver.newVersionCollection(knowledgeBase, nextVersion)
                    + "_b" + UUID.randomUUID().toString().replace("-", "");

            indexPublisher.requireCurrent(build);
            // 先独立提交归属意图，随后标记远端可能已收到请求；没有登记成功绝不创建集合。
            candidateHandle = vectorCollections.reserve(build,nextVersion,nextCollection);
            vectorCollections.creating(candidateHandle);
            AiVectorStoreResolver.VectorContext context =
                    vectorStoreResolver.resolveNewCollection(knowledgeBase, nextCollection, candidateHandle.ownershipMarker());
            indexPublisher.requireCurrent(build);
            vectorCollections.writing(candidateHandle);

            AiDocument query = new AiDocument();
            query.setKnowledgeBaseId(knowledgeBaseId);
            List<AiDocument> documents = aiDocumentMapper.selectDocumentList(query);
            List<KnowledgeIndexPublisher.Entry> manifest = indexPublisher.manifest(documents);
            indexPublisher.taskManifest(manifest);

            for (AiDocument document : documents) {
                if ("QUARANTINED".equals(document.getModerationStatus())
                        || "AUTO_DELETED".equals(document.getModerationStatus())) {
                    log.info(">>> 重建索引跳过已隔离/清理的文档: id={}, name={}", document.getId(), document.getName());
                    indexPublisher.taskDocumentCompleted();
                    continue;
                }
                // 缺失文件交给结构化解析明确失败，不能跳过后仍宣称整个新索引 READY。
                try {
                    indexDocument(document, knowledgeBase, nextVersion, context,
                            () -> indexPublisher.requireCurrent(claimed));
                    indexPublisher.taskDocumentCompleted();
                } catch (Exception e) {
                    // 文档运行作用域已按 owner 处理失败；不得在作用域外用旧实体回写。
                    if (indexPublisher.current(build)) documentWorker.failPending(knowledgeBase, document);
                    throw e;
                }
            }

            knowledgeBase.setIndexVersion(nextVersion);
            knowledgeBase.setVectorCollection(nextCollection);
            knowledgeBase.setIndexSignature(calculateIndexSignature(
                    knowledgeBase, descriptor));
            indexPublisher.requireCurrent(build);
            vectorCollections.built(candidateHandle);
            indexPublisher.activate(build, knowledgeBase, manifest);
            log.info(">>> 知识库 {} 索引版本 {} 构建成功，collection={}",
                    knowledgeBaseId, nextVersion, nextCollection);
        } catch (Exception e) {
            log.error(">>> 知识库 {} 索引重建失败", knowledgeBaseId, e);
            try {vectorCollections.abandon(candidateHandle);} catch (RuntimeException registryFailure) {
                // 登记存储不可用时保留原状态，不谎称远端已回滚，不重试远端写入或删除。
                log.warn("候选集合失败收尾未完成，需后续核验登记状态");
            }
            if (build != null) indexPublisher.failed(build);
        } finally {
            ACTIVE_REBUILDS.remove(knowledgeBaseId);
        }
        return true;
    }

    private void indexDocument(
            AiDocument doc,
            AiKnowledgeBase knowledgeBase,
            Long indexVersion,
            AiVectorStoreResolver.VectorContext context)
    {
        indexDocument(doc, knowledgeBase, indexVersion, context, () -> { });
    }

    private void indexDocument(
            AiDocument doc,
            AiKnowledgeBase knowledgeBase,
            Long indexVersion,
            AiVectorStoreResolver.VectorContext context,
            Runnable checkBuild)
    {
        checkBuild.run();
        documentWorker.run(knowledgeBase, doc, () -> indexOwnedDocument(doc, knowledgeBase, indexVersion, context, () -> {
            checkBuild.run();
            documentWorker.check();
        }));
    }

    private void indexOwnedDocument(
            AiDocument doc,
            AiKnowledgeBase knowledgeBase,
            Long indexVersion,
            AiVectorStoreResolver.VectorContext context,
            Runnable checkBuild)
    {
        log.info(">>> 开始安全检测与向量化文档: {}, collection={}", doc.getName(), context.collectionName());
        doc.setStatus("1");
        doc.setModerationStatus("SCANNING");
        documentWorker.mutate(() -> {
            if (aiDocumentMapper.update(null, new LambdaUpdateWrapper<AiDocument>()
                .eq(AiDocument::getId, doc.getId()).eq(AiDocument::getKnowledgeBaseId, knowledgeBase.getId())
                .and(state -> state.isNull(AiDocument::getModerationStatus).or()
                        .notIn(AiDocument::getModerationStatus, "QUARANTINED", "AUTO_DELETED"))
                .set(AiDocument::getStatus, "1").set(AiDocument::getModerationStatus, "SCANNING")) != 1)
                throw new IllegalStateException("文档已失效或隔离，停止导入");
        });

        java.nio.file.Path filePath = (doc.getFileUrl() != null && !doc.getFileUrl().isBlank())
                ? java.nio.file.Path.of(doc.getFileUrl()) : null;
        KnowledgeDocumentParser.Result parsed = documentParser.parse(filePath, doc.getName());
        checkBuild.run();
        if (!parsed.success()) {
            doc.setModerationStatus("SCAN_FAILED");
            doc.setStatus("3");
            documentWorker.mutate(() -> {
                if (aiDocumentMapper.update(null, new LambdaUpdateWrapper<AiDocument>()
                    .eq(AiDocument::getId, doc.getId()).eq(AiDocument::getKnowledgeBaseId, knowledgeBase.getId())
                    .eq(AiDocument::getModerationStatus, "SCANNING").eq(AiDocument::getStatus, "1")
                    .set(AiDocument::getModerationStatus, "SCAN_FAILED").set(AiDocument::getStatus, "3")) != 1)
                    throw new IllegalStateException("文档状态已变化，停止记录解析失败");
                documentSnapshots.parseFailed(knowledgeBase, doc, indexVersion, context.collectionName(), parsed.errorCode());
            });
            throw new IllegalStateException("知识库文档解析失败：" + parsed.errorCode());
        }
        String content = parsed.content();

        com.polaris.ai.safety.dto.ModerationResult moderation = knowledgeModerationGuard.moderateContent(
                doc,
                content,
                moderationFacade
        );

        checkBuild.run();
        if (!moderation.isAllowed()) {
            documentWorker.mutate(() -> indexPublisher.completeImport(() -> knowledgeModerationGuard.apply(doc, moderation, filePath)));
            log.warn(">>> 知识库文档 {} 安全检测未通过: {}", doc.getName(), moderation.finalAction());
            return;
        }
        documentWorker.mutate(() -> knowledgeModerationGuard.apply(doc, moderation, filePath));

        Document document = Document.from(content);
        document.metadata().put("knowledge_base_id", doc.getKnowledgeBaseId().toString());
        document.metadata().put("document_id", doc.getId().toString());
        document.metadata().put("doc_id", doc.getId().toString());
        document.metadata().put("doc_name", doc.getName());
        document.metadata().put("index_version", String.valueOf(indexVersion));
        if (knowledgeBase.getDeptId() != null) {
            document.metadata().put("dept_id", knowledgeBase.getDeptId().toString());
        }

        // 在高重叠配置下先拒绝明显超出快照预算的输入，避免先生成巨量分块。
        if ((long) content.length() / Math.max(1, knowledgeBase.getChunkSize() - knowledgeBase.getChunkOverlap()) > 2000) {
            documentWorker.mutate(() -> documentSnapshots.parseFailed(knowledgeBase, doc, indexVersion, context.collectionName(), "CHUNK_LIMIT"));
            throw new IllegalStateException("分块数量超出本批资源预算");
        }
        DocumentSplitter splitter = createSplitter(knowledgeBase);
        List<TextSegment> segments = splitter.split(document);
        List<TextSegment> indexedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            if (segment.text() == null || segment.text().trim().isEmpty()) {
                continue;
            }
            segment.metadata().put("chunk_no", String.valueOf(i));
            indexedSegments.add(segment);
        }
        if (indexedSegments.isEmpty() || indexedSegments.size() > 2000) {
            documentWorker.mutate(() -> documentSnapshots.parseFailed(knowledgeBase, doc, indexVersion, context.collectionName(), "CHUNK_LIMIT"));
            throw new IllegalStateException("文档未生成可用的向量分片");
        }

        List<String> allPointIds = new ArrayList<>();
        for (int i = 0; i < indexedSegments.size(); i++)
            allPointIds.add(stablePointId(context.collectionName(), doc.getId(), i, indexedSegments.get(i).text()));
        KnowledgeDocumentSnapshot snapshot = documentWorker.readOrWrite(() -> documentSnapshots.prepare(knowledgeBase, doc, indexVersion,
                context.collectionName(), parsed, indexedSegments, allPointIds));
        try {
            int batchSize = context.descriptor().batchSize();
            if (batchSize < 1) throw new IllegalStateException("向量批大小无效");
            for (int offset = 0; offset < indexedSegments.size(); offset += batchSize) {
                checkBuild.run();
                int end = Math.min(offset + batchSize, indexedSegments.size());
                List<TextSegment> batch = new ArrayList<>(indexedSegments.subList(offset, end));
                List<Embedding> embeddings = context.embeddingModel().embedAll(batch).content();
                checkBuild.run();
                if (embeddings == null || embeddings.size() != batch.size()
                        || embeddings.stream().anyMatch(embedding -> embedding == null || embedding.dimension() != context.descriptor().dimension()
                        || containsInvalidCoordinate(embedding.vector()))) {
                    throw new IllegalStateException("向量模型返回数量、维度或坐标无效");
                }
                context.embeddingStore().addAll(new ArrayList<>(allPointIds.subList(offset, end)), embeddings, batch);
            }
            checkBuild.run();
            documentWorker.mutate(() -> indexPublisher.completeImport(() -> {
                documentSnapshots.ready(snapshot);
                if (aiDocumentMapper.update(null, new LambdaUpdateWrapper<AiDocument>()
                    .eq(AiDocument::getId, doc.getId()).eq(AiDocument::getKnowledgeBaseId, knowledgeBase.getId())
                    .eq(AiDocument::getStatus, "1").eq(AiDocument::getModerationStatus, "SAFE")
                    .set(AiDocument::getStatus, "2").set(AiDocument::getWordCount, content.length())) != 1)
                    throw new IllegalStateException("文档状态已变化");
            }));
            doc.setStatus("2");
            doc.setWordCount(content.length());
        } catch (Exception e) {
            documentSnapshots.failed(snapshot);
            throw e;
        }
        log.info(">>> 文档 {} 向量化入库成功，共 {} 个分片", doc.getName(), indexedSegments.size());
    }

    private boolean containsInvalidCoordinate(float[] values) {
        for (float value : values) if (!Float.isFinite(value)) return true;
        return false;
    }

    private DocumentSplitter createSplitter(AiKnowledgeBase knowledgeBase)
    {
        if (!"RECURSIVE".equalsIgnoreCase(knowledgeBase.getSplitterType())) {
            throw new IllegalArgumentException("暂不支持切片算法: " + knowledgeBase.getSplitterType());
        }
        return DocumentSplitters.recursive(knowledgeBase.getChunkSize(), knowledgeBase.getChunkOverlap());
    }

    private void applyDefaultsAndValidate(AiKnowledgeBase knowledgeBase)
    {
        if (knowledgeBase.getChunkSize() == null) {
            knowledgeBase.setChunkSize(DEFAULT_CHUNK_SIZE);
        }
        if (knowledgeBase.getChunkOverlap() == null) {
            knowledgeBase.setChunkOverlap(DEFAULT_CHUNK_OVERLAP);
        }
        if (knowledgeBase.getSplitterType() == null || knowledgeBase.getSplitterType().trim().isEmpty()) {
            knowledgeBase.setSplitterType("RECURSIVE");
        }
        if (knowledgeBase.getRetrievalTopK() == null) {
            knowledgeBase.setRetrievalTopK(DEFAULT_RETRIEVAL_TOP_K);
        }
        if (knowledgeBase.getRetrievalMinScore() == null) {
            knowledgeBase.setRetrievalMinScore(DEFAULT_RETRIEVAL_MIN_SCORE);
        }
        if (knowledgeBase.getIndexVersion() == null) {
            knowledgeBase.setIndexVersion(0L);
        }
        if (knowledgeBase.getChunkSize() < 50 || knowledgeBase.getChunkSize() > 20000) {
            throw new IllegalArgumentException("切片大小必须处于 50-20000 字符");
        }
        if (knowledgeBase.getChunkOverlap() < 0
                || knowledgeBase.getChunkOverlap() >= knowledgeBase.getChunkSize()) {
            throw new IllegalArgumentException("切片重叠必须大于等于 0 且小于切片大小");
        }
        if (knowledgeBase.getRetrievalTopK() < 1 || knowledgeBase.getRetrievalTopK() > 50) {
            throw new IllegalArgumentException("最大召回数量必须处于 1-50");
        }
        if (knowledgeBase.getRetrievalMinScore() < 0 || knowledgeBase.getRetrievalMinScore() > 1) {
            throw new IllegalArgumentException("最低相似度必须处于 0-1");
        }
    }

    private void mergeIndexDefaults(AiKnowledgeBase target, AiKnowledgeBase existing)
    {
        if (target.getEmbeddingModelId() == null) target.setEmbeddingModelId(existing.getEmbeddingModelId());
        if (target.getEmbeddingDimension() == null) target.setEmbeddingDimension(existing.getEmbeddingDimension());
        if (target.getEmbeddingModelRevision() == null) target.setEmbeddingModelRevision(existing.getEmbeddingModelRevision());
        if (target.getEmbeddingSchemaHash() == null) target.setEmbeddingSchemaHash(existing.getEmbeddingSchemaHash());
        if (target.getVectorCollection() == null) target.setVectorCollection(existing.getVectorCollection());
        if (target.getChunkSize() == null) target.setChunkSize(existing.getChunkSize());
        if (target.getChunkOverlap() == null) target.setChunkOverlap(existing.getChunkOverlap());
        if (target.getSplitterType() == null) target.setSplitterType(existing.getSplitterType());
        if (target.getRetrievalTopK() == null) target.setRetrievalTopK(existing.getRetrievalTopK());
        if (target.getRetrievalMinScore() == null) target.setRetrievalMinScore(existing.getRetrievalMinScore());
        if (target.getIndexVersion() == null) target.setIndexVersion(existing.getIndexVersion());
        if (target.getIndexSignature() == null) target.setIndexSignature(existing.getIndexSignature());
        if (target.getIndexStatus() == null) target.setIndexStatus(existing.getIndexStatus());
    }

    private void validateEmbeddingModelScope(
            AiKnowledgeBase knowledgeBase,
            EmbeddingRuntimeDescriptor descriptor)
    {
        boolean admin = false;
        try {
            CallerContext ctx = CallerContextHolder.get();
            admin = ctx != null && ctx.isSuperAdmin();
        } catch (Exception ignored) {
            // Background rebuilds use the already validated persisted binding.
        }
        if (!admin && descriptor.deptId() != null
                && !Objects.equals(descriptor.deptId(), knowledgeBase.getDeptId())) {
            throw new IllegalArgumentException("无权绑定该部门的向量模型");
        }
    }

    private String calculateIndexSignature(
            AiKnowledgeBase knowledgeBase,
            EmbeddingRuntimeDescriptor descriptor)
    {
        String source = descriptor.modelId() + "|"
                + descriptor.modelRevision() + "|"
                + descriptor.schemaHash() + "|"
                + descriptor.dimension() + "|" + knowledgeBase.getChunkSize() + "|"
                + knowledgeBase.getChunkOverlap() + "|" + knowledgeBase.getSplitterType();
        return sha256(source);
    }

    private String stablePointId(String collection, Long documentId, int chunkNo, String text)
    {
        String source = collection + "|" + documentId + "|" + chunkNo + "|" + sha256(text);
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String sha256(String value)
    {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算索引签名失败", e);
        }
    }

    private String abbreviate(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 监听 Spring 启动就绪事件
     * 内存模式默认重建；Qdrant 等持久化模式默认跳过。
     * 首次迁移已有文档时，可临时将 rebuild-on-startup 设为 always。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void autoRebuildInMemoryEmbeddings()
    {
        if (!vectorStoreProperties.shouldRebuildOnStartup()) {
            log.info(">>> 向量存储类型为 {}，跳过启动向量重建", vectorStoreProperties.getType());
            return;
        }

        log.info(">>> 检测到系统启动，提交 {} 知识库索引任务...", vectorStoreProperties.getType());
        List<AiKnowledgeBase> knowledgeBases = aiKnowledgeMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeBase>().orderByAsc(AiKnowledgeBase::getId));
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            log.info(">>> 没有知识库需要加载到向量存储");
            return;
        }

        // 不自建裸线程，不接管中断任务；仅保存新任务并交给专用有界线程池。
        for (AiKnowledgeBase knowledgeBase : knowledgeBases) {
            try {
                var task = rebuildTasks.submitStartup(knowledgeBase);
                log.info(">>> 启动索引提交：knowledgeId={}, state={}", knowledgeBase.getId(), task == null ? "SKIPPED_ACTIVE" : task.state());
            } catch (RuntimeException e) {
                log.warn(">>> 启动索引任务保存失败：knowledgeId={}，请检查数据库版本后显式重建", knowledgeBase.getId());
            }
        }
    }
}
