package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.rag.AiVectorStoreProperties;
import com.polaris.ai.rag.AiVectorStoreResolver;
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
import org.springframework.scheduling.annotation.Async;
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
    private com.polaris.ai.attachment.AttachmentTextExtractor attachmentTextExtractor;

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
        AiModelConfig modelConfig = vectorStoreResolver.resolveModelConfig(kb.getEmbeddingModelId());
        validateEmbeddingModelScope(kb, modelConfig);
        kb.setEmbeddingModelId(modelConfig.getId());
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
        AiModelConfig modelConfig = vectorStoreResolver.resolveModelConfig(kb.getEmbeddingModelId());
        validateEmbeddingModelScope(kb, modelConfig);
        kb.setEmbeddingModelId(modelConfig.getId());
        boolean indexConfigChanged = !Objects.equals(existing.getEmbeddingModelId(), kb.getEmbeddingModelId())
                || !Objects.equals(existing.getChunkSize(), kb.getChunkSize())
                || !Objects.equals(existing.getChunkOverlap(), kb.getChunkOverlap())
                || !Objects.equals(existing.getSplitterType(), kb.getSplitterType());
        if (indexConfigChanged) {
            kb.setIndexStatus("STALE");
            kb.setIndexError(null);
        }
        int result = aiKnowledgeMapper.updateById(kb);
        if (indexConfigChanged) {
            updateIndexState(kb.getId(), "STALE", "索引配置已变更，请重建知识库索引");
        }
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
                .set(AiKnowledgeBase::getIndexStatus, "STALE")
                .set(AiKnowledgeBase::getIndexError, abbreviate(reason, 1000)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteKnowledgeBase(Long id)
    {
        AiKnowledgeBase knowledgeBase = aiKnowledgeMapper.selectById(id);
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
        return aiKnowledgeMapper.deleteById(id);
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
    public int insertDocument(AiDocument doc)
    {
        doc.setStatus("0"); // 待解析
        doc.setWordCount(0);
        return aiDocumentMapper.insert(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDocument(Long id)
    {
        AiDocument document = aiDocumentMapper.selectById(id);
        // 1. 从向量库清除该文档的向量
        if (document != null) {
            AiKnowledgeBase knowledgeBase = aiKnowledgeMapper.selectById(document.getKnowledgeBaseId());
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
        return aiDocumentMapper.deleteById(id);
    }

    // ================================================================
    //  RAG 核心：切片与向量化
    // ================================================================

    /**
     * 异步解析文档并向量化
     */
    @Override
    @Async("threadPoolTaskExecutor")
    public void importDocumentAsync(Long docId)
    {
        AiDocument doc = aiDocumentMapper.selectById(docId);
        if (doc == null) {
            log.error("未找到待导入的文档，ID: {}", docId);
            return;
        }
        AiKnowledgeBase knowledgeBase = aiKnowledgeMapper.selectById(doc.getKnowledgeBaseId());
        if (knowledgeBase == null) {
            markDocumentFailed(doc, "所属知识库不存在");
            return;
        }
        try {
            applyDefaultsAndValidate(knowledgeBase);
            if (knowledgeBase.getVectorCollection() == null
                    || !"READY".equalsIgnoreCase(knowledgeBase.getIndexStatus())) {
                boolean rebuiltHere = rebuildKnowledgeBase(knowledgeBase.getId());
                if (!rebuiltHere) {
                    waitForActiveRebuild(knowledgeBase.getId());
                    AiDocument refreshedDocument = aiDocumentMapper.selectById(docId);
                    AiKnowledgeBase refreshedKnowledgeBase = aiKnowledgeMapper.selectById(knowledgeBase.getId());
                    if (refreshedDocument != null && !"2".equals(refreshedDocument.getStatus())
                            && refreshedKnowledgeBase != null
                            && "READY".equalsIgnoreCase(refreshedKnowledgeBase.getIndexStatus())) {
                        AiVectorStoreResolver.VectorContext refreshedContext =
                                vectorStoreResolver.resolve(refreshedKnowledgeBase);
                        indexDocument(refreshedDocument, refreshedKnowledgeBase,
                                refreshedKnowledgeBase.getIndexVersion(), refreshedContext);
                    }
                }
                return;
            }
            AiVectorStoreResolver.VectorContext context = vectorStoreResolver.resolve(knowledgeBase);
            indexDocument(doc, knowledgeBase, knowledgeBase.getIndexVersion(), context);
        } catch (Exception e) {
            log.error(">>> 文档 {} 向量化失败", doc.getName(), e);
            markDocumentFailed(doc, e.getMessage());
        }
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void rebuildKnowledgeBaseAsync(Long knowledgeBaseId)
    {
        rebuildKnowledgeBase(knowledgeBaseId);
    }

    private boolean rebuildKnowledgeBase(Long knowledgeBaseId)
    {
        if (knowledgeBaseId == null || !ACTIVE_REBUILDS.add(knowledgeBaseId)) {
            log.info(">>> 知识库 {} 已有重建任务运行，忽略重复请求", knowledgeBaseId);
            return false;
        }
        AiKnowledgeBase knowledgeBase = aiKnowledgeMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            ACTIVE_REBUILDS.remove(knowledgeBaseId);
            return true;
        }

        try {
            applyDefaultsAndValidate(knowledgeBase);
            AiModelConfig modelConfig = vectorStoreResolver.resolveModelConfig(knowledgeBase.getEmbeddingModelId());
            knowledgeBase.setEmbeddingModelId(modelConfig.getId());
            long nextVersion = knowledgeBase.getIndexVersion() == null
                    ? 1L : knowledgeBase.getIndexVersion() + 1L;
            String nextCollection = vectorStoreResolver.newVersionCollection(knowledgeBase, nextVersion);

            updateIndexState(knowledgeBaseId, "BUILDING", null);
            AiVectorStoreResolver.VectorContext context =
                    vectorStoreResolver.resolveForCollection(knowledgeBase, nextCollection);

            AiDocument query = new AiDocument();
            query.setKnowledgeBaseId(knowledgeBaseId);
            List<AiDocument> documents = aiDocumentMapper.selectDocumentList(query);
            if (documents == null || documents.isEmpty()) {
                knowledgeBase.setIndexVersion(nextVersion);
                knowledgeBase.setVectorCollection(nextCollection);
                knowledgeBase.setIndexSignature(calculateIndexSignature(knowledgeBase, modelConfig));
                knowledgeBase.setIndexStatus("EMPTY");
                knowledgeBase.setIndexError(null);
                aiKnowledgeMapper.updateById(knowledgeBase);
                updateIndexState(knowledgeBaseId, "EMPTY", null);
                return true;
            }

            for (AiDocument document : documents) {
                if ("QUARANTINED".equals(document.getModerationStatus())
                        || "AUTO_DELETED".equals(document.getModerationStatus())) {
                    log.info(">>> 重建索引跳过已隔离/清理的文档: id={}, name={}", document.getId(), document.getName());
                    continue;
                }
                if (document.getFileUrl() == null || !java.nio.file.Files.exists(java.nio.file.Path.of(document.getFileUrl()))) {
                    log.warn(">>> 重建索引跳过物理文件不存在的文档: id={}, name={}", document.getId(), document.getName());
                    continue;
                }
                try {
                    indexDocument(document, knowledgeBase, nextVersion, context);
                } catch (Exception e) {
                    markDocumentFailed(document, e.getMessage());
                    throw e;
                }
            }

            knowledgeBase.setIndexVersion(nextVersion);
            knowledgeBase.setVectorCollection(nextCollection);
            knowledgeBase.setIndexSignature(calculateIndexSignature(knowledgeBase, modelConfig));
            knowledgeBase.setIndexStatus("READY");
            knowledgeBase.setIndexError(null);
            aiKnowledgeMapper.updateById(knowledgeBase);
            updateIndexState(knowledgeBaseId, "READY", null);
            log.info(">>> 知识库 {} 索引版本 {} 构建成功，collection={}",
                    knowledgeBaseId, nextVersion, nextCollection);
        } catch (Exception e) {
            log.error(">>> 知识库 {} 索引重建失败", knowledgeBaseId, e);
            updateIndexState(knowledgeBaseId, "FAILED", abbreviate(e.getMessage(), 1000));
        } finally {
            ACTIVE_REBUILDS.remove(knowledgeBaseId);
        }
        return true;
    }

    private void waitForActiveRebuild(Long knowledgeBaseId)
    {
        long deadline = System.currentTimeMillis() + 60_000L;
        while (ACTIVE_REBUILDS.contains(knowledgeBaseId) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待知识库重建时被中断", e);
            }
        }
        if (ACTIVE_REBUILDS.contains(knowledgeBaseId)) {
            throw new IllegalStateException("等待知识库重建超时");
        }
    }

    private void indexDocument(
            AiDocument doc,
            AiKnowledgeBase knowledgeBase,
            Long indexVersion,
            AiVectorStoreResolver.VectorContext context)
    {
        log.info(">>> 开始安全检测与向量化文档: {}, collection={}", doc.getName(), context.collectionName());
        doc.setStatus("1");
        doc.setModerationStatus("SCANNING");
        aiDocumentMapper.updateById(doc);

        java.nio.file.Path filePath = (doc.getFileUrl() != null && !doc.getFileUrl().isBlank())
                ? java.nio.file.Path.of(doc.getFileUrl()) : null;
        String content;
        try {
            content = attachmentTextExtractor.extract(filePath, doc.getName());
            if (content == null || content.trim().isEmpty() || content.startsWith("[解析附件时发生错误")) {
                doc.setModerationStatus("SCAN_FAILED");
                doc.setStatus("3");
                aiDocumentMapper.updateById(doc);
                throw new IllegalStateException("文件内容提取为空或解析错误");
            }
        } catch (Exception e) {
            doc.setModerationStatus("SCAN_FAILED");
            doc.setStatus("3");
            aiDocumentMapper.updateById(doc);
            throw e;
        }

        com.polaris.ai.safety.dto.ModerationResult moderation = knowledgeModerationGuard.moderateContent(
                doc,
                content,
                moderationFacade
        );

        knowledgeModerationGuard.apply(doc, moderation, filePath);
        if (!moderation.isAllowed()) {
            log.warn(">>> 知识库文档 {} 安全检测未通过: {}", doc.getName(), moderation.finalAction());
            return;
        }

        Document document = Document.from(content);
        document.metadata().put("knowledge_base_id", doc.getKnowledgeBaseId().toString());
        document.metadata().put("document_id", doc.getId().toString());
        document.metadata().put("doc_id", doc.getId().toString());
        document.metadata().put("doc_name", doc.getName());
        document.metadata().put("index_version", String.valueOf(indexVersion));
        if (knowledgeBase.getDeptId() != null) {
            document.metadata().put("dept_id", knowledgeBase.getDeptId().toString());
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
        if (indexedSegments.isEmpty()) {
            throw new IllegalStateException("文档未生成可用的向量分片");
        }

        int batchSize = context.modelConfig().getEmbeddingBatchSize() == null
                ? 16 : context.modelConfig().getEmbeddingBatchSize();
        for (int offset = 0; offset < indexedSegments.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, indexedSegments.size());
            List<TextSegment> batch = new ArrayList<>(indexedSegments.subList(offset, end));
            List<Embedding> embeddings = context.embeddingModel().embedAll(batch).content();
            if (embeddings == null || embeddings.size() != batch.size()) {
                throw new IllegalStateException("向量模型返回数量与分片数量不一致");
            }
            List<String> pointIds = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                pointIds.add(stablePointId(context.collectionName(), doc.getId(), offset + i, batch.get(i).text()));
            }
            context.embeddingStore().addAll(pointIds, embeddings, batch);
        }

        doc.setStatus("2");
        doc.setWordCount(content.length());
        aiDocumentMapper.updateById(doc);
        log.info(">>> 文档 {} 向量化入库成功，共 {} 个分片", doc.getName(), indexedSegments.size());
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

    private void validateEmbeddingModelScope(AiKnowledgeBase knowledgeBase, AiModelConfig modelConfig)
    {
        boolean admin = false;
        try {
            CallerContext ctx = CallerContextHolder.get();
            admin = ctx != null && ctx.isSuperAdmin();
        } catch (Exception ignored) {
            // Background rebuilds use the already validated persisted binding.
        }
        if (!admin && modelConfig.getDeptId() != null
                && !Objects.equals(modelConfig.getDeptId(), knowledgeBase.getDeptId())) {
            throw new IllegalArgumentException("无权绑定该部门的向量模型");
        }
    }

    private String calculateIndexSignature(AiKnowledgeBase knowledgeBase, AiModelConfig modelConfig)
    {
        String source = modelConfig.getProvider() + "|" + modelConfig.getModelName() + "|"
                + modelConfig.getEmbeddingDimension() + "|" + knowledgeBase.getChunkSize() + "|"
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

    private void updateIndexState(Long knowledgeBaseId, String status, String error)
    {
        aiKnowledgeMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getId, knowledgeBaseId)
                .set(AiKnowledgeBase::getIndexStatus, status)
                .set(AiKnowledgeBase::getIndexError, error));
    }

    private void markDocumentFailed(AiDocument document, String error)
    {
        document.setStatus("3");
        document.setRemark(abbreviate(error, 500));
        aiDocumentMapper.updateById(document);
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

        log.info(">>> 检测到系统启动，开始按知识库重建 {} 向量索引...", vectorStoreProperties.getType());
        List<AiKnowledgeBase> knowledgeBases = aiKnowledgeMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeBase>().orderByAsc(AiKnowledgeBase::getId));
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            log.info(">>> 没有知识库需要加载到向量存储");
            return;
        }

        // 异步后台加载，不阻塞 Spring 启动线程
        new Thread(() -> {
            int successCount = 0;
            for (AiKnowledgeBase knowledgeBase : knowledgeBases) {
                rebuildKnowledgeBase(knowledgeBase.getId());
                AiKnowledgeBase refreshed = aiKnowledgeMapper.selectById(knowledgeBase.getId());
                if (refreshed != null
                        && ("READY".equals(refreshed.getIndexStatus())
                        || "EMPTY".equals(refreshed.getIndexStatus()))) {
                    successCount++;
                }
            }
            log.info(">>> 向量索引重建完毕，成功加载 {}/{} 个知识库", successCount, knowledgeBases.size());
        }, "polaris-vector-rebuild").start();
    }
}
