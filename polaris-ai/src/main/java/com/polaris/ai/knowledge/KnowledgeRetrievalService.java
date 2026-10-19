package com.polaris.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.common.exception.ServiceException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.*;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/** 仅接受入口已授权的知识库快照；不从请求解析租户、collection 或授权范围。 */
@Service
public class KnowledgeRetrievalService {
    private final AiKnowledgeMapper knowledgeMapper;
    private final AiDocumentMapper documentMapper;

    public KnowledgeRetrievalService(AiKnowledgeMapper knowledgeMapper, AiDocumentMapper documentMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.documentMapper = documentMapper;
    }

    public SearchResult search(AiKnowledgeBase snapshot, AiVectorStoreResolver.VectorContext vector,
                               String query, int candidateLimit, double minScore, List<String> documentIds) {
        return search(snapshot, vector, query, candidateLimit, minScore, documentIds, List.of());
    }

    /** 显式关联补全：同一问题向量、同库同索引，最多三次定向查询；不推断关系或扩张过滤范围。 */
    public SearchResult search(AiKnowledgeBase snapshot, AiVectorStoreResolver.VectorContext vector,
                               String query, int candidateLimit, double minScore, List<String> documentIds,
                               List<String> relatedDocumentIds) {
        return search(snapshot, vector, query, candidateLimit, minScore, documentIds, relatedDocumentIds, () -> { });
    }

    /** 协作节点在每个外部检索阶段之间复查预算与取消，不改变原有检索范围。 */
    public SearchResult search(AiKnowledgeBase snapshot, AiVectorStoreResolver.VectorContext vector,
                               String query, int candidateLimit, double minScore, List<String> documentIds,
                               List<String> relatedDocumentIds, Runnable activeCheck) {
        activeCheck.run();
        List<String> related = validateRelatedPlan(relatedDocumentIds, documentIds, candidateLimit);
        if (query == null || query.isBlank() || query.length() > 16000
                || candidateLimit < 1 || candidateLimit > 50
                || !Double.isFinite(minScore) || minScore < 0 || minScore > 1) {
            throw new ServiceException("检索参数无效", 400);
        }
        requireCurrent(snapshot);
        if (!related.isEmpty()) validateSources(snapshot, related.stream().map(Long::valueOf).toList());
        if (vector == null || !Objects.equals(vector.collectionName(), snapshot.getVectorCollection())
                || !Objects.equals(vector.descriptor().modelId(), snapshot.getEmbeddingModelId())
                || !Objects.equals(vector.descriptor().modelRevision(), snapshot.getEmbeddingModelRevision())
                || !Objects.equals(vector.descriptor().schemaHash(), snapshot.getEmbeddingSchemaHash())
                || !Objects.equals(vector.descriptor().dimension(), snapshot.getEmbeddingDimension())) {
            throw new ServiceException("知识库向量快照已变化，请刷新后重试", 409);
        }
        long started = System.nanoTime();
        activeCheck.run();
        Embedding embedding;
        try {
            embedding = vector.embeddingModel().embed(query).content();
            if (embedding == null || embedding.dimension() != vector.descriptor().dimension()) {
                throw new IllegalStateException("Invalid embedding");
            }
        } catch (RuntimeException e) {
            throw new ServiceException("问题向量化失败，请检查当前向量模型后重试", 502);
        }
        long embeddingMs = elapsed(started);
        activeCheck.run();
        requireCurrent(snapshot);
        long searchStarted = System.nanoTime();
        QueryResult primary = queryStore(snapshot, vector, embedding, candidateLimit, minScore, documentIds);
        activeCheck.run();
        if (related.isEmpty()) return new SearchResult(primary.matches(), primary.candidateCount(), primary.filteredCount(),
                embeddingMs, elapsed(searchStarted), elapsed(started));
        List<EmbeddingMatch<TextSegment>> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (!primary.matches().isEmpty()) addCoverage(selected, seen, primary.matches().get(0), 0);
        int count = primary.candidateCount(), filtered = primary.filteredCount(), vectorQueries = 1;
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < related.size(); i++) {
            activeCheck.run();
            requireCurrent(snapshot);
            String id = related.get(i);
            var existing = primary.matches().stream().filter(match -> id.equals(match.embedded().metadata().getString("doc_id"))).findFirst();
            QueryResult supplemental;
            if (existing.isPresent()) supplemental = new QueryResult(List.of(existing.get()), 0, 0);
            else { supplemental = queryStore(snapshot, vector, embedding, 1, minScore, List.of(id)); vectorQueries++; }
            count += supplemental.candidateCount(); filtered += supplemental.filteredCount();
            if (supplemental.matches().isEmpty()) missing.add(id);
            else addCoverage(selected, seen, supplemental.matches().get(0), i + 1);
        }
        for (var match : primary.matches()) {
            if (selected.size() >= candidateLimit) break;
            addCoverage(selected, seen, match, 99);
        }
        validateSources(snapshot, selected.stream().map(match -> Long.valueOf(match.embedded().metadata().getString("doc_id"))).toList());
        activeCheck.run();
        return new SearchResult(List.copyOf(selected), count, filtered, embeddingMs, elapsed(searchStarted), elapsed(started),
                vectorQueries, List.copyOf(missing));
    }

    public static List<String> validateRelatedPlan(List<String> related, List<String> scope, int resultLimit) {
        if (related == null || related.isEmpty()) return List.of();
        if (related.size() > 3 || related.stream().anyMatch(id -> id == null || !id.matches("[1-9][0-9]{0,18}"))
                || new HashSet<>(related).size() != related.size() || resultLimit < related.size() + 1) {
            throw new ServiceException("关联补全最多指定 3 份不同文档，返回数量须为关联文档数加一或更多", 400);
        }
        try { related.forEach(Long::valueOf); } catch (NumberFormatException e) { throw new ServiceException("关联文档编号无效", 400); }
        if (scope != null && !scope.isEmpty() && !scope.containsAll(related)) {
            throw new ServiceException("关联文档不能超出显式指定的文档范围", 400);
        }
        return List.copyOf(related);
    }

    /** 模型准备前的只读范围检查；核心执行时还会重新核对。 */
    public void validateRelatedSources(AiKnowledgeBase snapshot, List<String> related, List<String> scope, int resultLimit) {
        List<String> ids = validateRelatedPlan(related, scope, resultLimit);
        if (!ids.isEmpty()) validateSources(snapshot, ids.stream().map(Long::valueOf).toList());
    }

    private void addCoverage(List<EmbeddingMatch<TextSegment>> selected, Set<String> seen,
                             EmbeddingMatch<TextSegment> match, int priority) {
        String key = match.embedded().metadata().getString("doc_id") + ":" + match.embedded().text();
        if (!seen.add(key)) return;
        Metadata metadata = Metadata.from(new HashMap<>(match.embedded().metadata().toMap()));
        metadata.put("coverage_priority", priority);
        selected.add(new EmbeddingMatch<>(match.score(), match.embeddingId(), match.embedding(), TextSegment.from(match.embedded().text(), metadata)));
    }

    private QueryResult queryStore(AiKnowledgeBase snapshot, AiVectorStoreResolver.VectorContext vector,
                                   Embedding embedding, int candidateLimit, double minScore, List<String> documentIds) {
        Filter filter = metadataKey("knowledge_base_id").isEqualTo(snapshot.getId().toString());
        if (documentIds != null && !documentIds.isEmpty()) {
            filter = filter.and(metadataKey("doc_id").isIn(documentIds));
        }
        EmbeddingSearchResult<TextSegment> response;
        try {
            response = vector.embeddingStore().search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding).maxResults(candidateLimit).minScore(minScore).filter(filter).build());
        } catch (RuntimeException e) {
            throw new ServiceException("向量检索服务暂不可用，请稍后重试", 502);
        }
        if (response == null || response.matches() == null) {
            throw new ServiceException("检索服务返回无效响应，请稍后重试", 502);
        }
        requireCurrent(snapshot);
        List<EmbeddingMatch<TextSegment>> matches = response.matches();
        Map<Long, AiDocument> documents = currentDocuments(snapshot, matches);
        List<EmbeddingMatch<TextSegment>> valid = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            Long documentId = documentId(match, snapshot);
            AiDocument document = documentId == null ? null : documents.get(documentId);
            if (documentId == null || document == null || !Objects.equals(document.getKnowledgeBaseId(), snapshot.getId())
                    || (documentIds != null && !documentIds.isEmpty() && !documentIds.contains(documentId.toString()))
                    || !"0".equals(document.getDelFlag()) || !"2".equals(document.getStatus())
                    || !"SAFE".equals(document.getModerationStatus())) continue;
            // 只输出允许的来源元数据，名称以当前文档记录为准，不携带私有路径等 payload。
            Metadata metadata = new Metadata();
            metadata.put("knowledge_base_id", snapshot.getId().toString());
            metadata.put("doc_id", documentId.toString());
            metadata.put("document_id", documentId.toString());
            metadata.put("doc_name", Objects.toString(document.getName(), "知识库文档"));
            metadata.put("index_version", snapshot.getIndexVersion().toString());
            String chunk = Objects.toString(match.embedded().metadata().toMap().get("chunk_no"), "");
            if (chunk != null && chunk.matches("[0-9]{1,9}")) metadata.put("chunk_no", chunk);
            valid.add(new EmbeddingMatch<>(match.score(), match.embeddingId(), match.embedding(),
                    TextSegment.from(match.embedded().text(), metadata)));
        }
        return new QueryResult(List.copyOf(valid), matches.size(), matches.size() - valid.size());
    }

    private Map<Long, AiDocument> currentDocuments(AiKnowledgeBase snapshot,
                                                   List<EmbeddingMatch<TextSegment>> matches) {
        Set<Long> ids = new LinkedHashSet<>();
        matches.forEach(match -> {
            Long id = documentId(match, snapshot);
            if (id != null) ids.add(id);
        });
        if (ids.isEmpty()) return Map.of();
        List<AiDocument> rows = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKnowledgeBaseId, snapshot.getId()).in(AiDocument::getId, ids)
                .eq(AiDocument::getStatus, "2").eq(AiDocument::getModerationStatus, "SAFE"));
        Map<Long, AiDocument> result = new HashMap<>();
        if (rows != null) rows.forEach(row -> result.put(row.getId(), row));
        return result;
    }

    /** 已召回上下文提交模型前的有效性检查；不产生向量化或向量库调用。 */
    public void validateSources(AiKnowledgeBase snapshot, List<Long> documentIds) {
        requireCurrent(snapshot);
        Set<Long> expected = new HashSet<>(documentIds);
        if (expected.isEmpty()) return;
        List<AiDocument> rows = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKnowledgeBaseId, snapshot.getId()).in(AiDocument::getId, expected)
                .eq(AiDocument::getStatus, "2").eq(AiDocument::getModerationStatus, "SAFE"));
        Set<Long> valid = new HashSet<>();
        if (rows != null) rows.forEach(row -> {
            if (Objects.equals(row.getKnowledgeBaseId(), snapshot.getId()) && "0".equals(row.getDelFlag())
                    && "2".equals(row.getStatus()) && "SAFE".equals(row.getModerationStatus())) valid.add(row.getId());
        });
        if (!valid.containsAll(expected)) throw new ServiceException("引用文档已失效，请重新检索", 409);
    }

    private Long documentId(EmbeddingMatch<TextSegment> match, AiKnowledgeBase snapshot) {
        if (match == null || match.embedded() == null || match.score() == null
                || !Double.isFinite(match.score()) || match.score() < 0 || match.score() > 1
                || match.embedded().text() == null || match.embedded().text().isBlank()) return null;
        try {
            Metadata metadata = match.embedded().metadata();
            if (metadata == null || !Objects.equals(metadata.getString("knowledge_base_id"), snapshot.getId().toString())
                    || !Objects.equals(metadata.getString("index_version"), snapshot.getIndexVersion().toString())) return null;
            String id = metadata.getString("doc_id");
            String alias = metadata.getString("document_id");
            if (id == null || !id.matches("[1-9][0-9]{0,18}") || (alias != null && !alias.equals(id))) return null;
            return Long.valueOf(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void requireCurrent(AiKnowledgeBase snapshot) {
        CallerContext caller = CallerContextHolder.get();
        if (caller == null || snapshot == null || snapshot.getId() == null
                || (!caller.isSuperAdmin() && !Objects.equals(caller.getTenantId(),
                snapshot.getTenantId() == null ? null : snapshot.getTenantId().toString()))) {
            throw new ServiceException("知识库不存在或无权访问", 404);
        }
        AiKnowledgeBase current = knowledgeMapper.selectWorkflowResource(snapshot.getTenantId(), snapshot.getId());
        if (current == null || !Objects.equals(current.getTenantId(), snapshot.getTenantId())
                || !"0".equals(current.getDelFlag())) {
            throw new ServiceException("知识库不存在或无权访问", 404);
        }
        if (!"READY".equals(current.getIndexStatus()) || snapshot.getIndexVersion() == null
                || snapshot.getIndexVersion() <= 0 || snapshot.getVectorCollection() == null
                || !Objects.equals(current.getIndexVersion(), snapshot.getIndexVersion())
                || !Objects.equals(current.getVectorCollection(), snapshot.getVectorCollection())
                || !Objects.equals(current.getEmbeddingModelId(), snapshot.getEmbeddingModelId())
                || !Objects.equals(current.getEmbeddingModelRevision(), snapshot.getEmbeddingModelRevision())
                || !Objects.equals(current.getEmbeddingDimension(), snapshot.getEmbeddingDimension())
                || !Objects.equals(current.getEmbeddingSchemaHash(), snapshot.getEmbeddingSchemaHash())) {
            throw new ServiceException("知识库索引未就绪或版本已变化，请刷新后重试", 409);
        }
    }

    private static long elapsed(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000L); }

    private record QueryResult(List<EmbeddingMatch<TextSegment>> matches, int candidateCount, int filteredCount) { }

    public record SearchResult(List<EmbeddingMatch<TextSegment>> matches, int candidateCount, int filteredCount,
                               long embeddingMs, long searchMs, long durationMs, int vectorQueryCount,
                               List<String> missingRelatedDocumentIds) {
        public SearchResult(List<EmbeddingMatch<TextSegment>> matches, int candidateCount, int filteredCount,
                            long embeddingMs, long searchMs, long durationMs) {
            this(matches, candidateCount, filteredCount, embeddingMs, searchMs, durationMs, 1, List.of());
        }
    }
}
