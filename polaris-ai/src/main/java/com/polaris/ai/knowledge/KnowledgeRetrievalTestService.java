package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.ai.service.IAiKnowledgeService;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.*;

/** 管理员只检索诊断；不生成回答、不保存问题或正文、不修改知识库参数。 */
@Service
public class KnowledgeRetrievalTestService {
    private final IAiKnowledgeService knowledgeService;
    private final AiVectorStoreResolver vectorResolver;
    private final KnowledgeRetrievalService retrieval;
    private final Set<String> active = new HashSet<>();
    private final Map<String, Long> recent = new HashMap<>();

    public KnowledgeRetrievalTestService(IAiKnowledgeService knowledgeService,
                                         AiVectorStoreResolver vectorResolver, KnowledgeRetrievalService retrieval) {
        this.knowledgeService = knowledgeService;
        this.vectorResolver = vectorResolver;
        this.retrieval = retrieval;
    }

    public TestResponse test(Long knowledgeId, TestRequest request) {
        CallerContext caller = CallerContextHolder.get();
        if (caller == null || caller.getUserId() == null || caller.getUserId() <= 0
                || !(caller.isSuperAdmin() || (caller.isPlatformMode() && caller.isTenantAdmin()))) {
            throw new ServiceException("仅系统超级管理员或当前租户管理员可进行检索测试", 403);
        }
        if (knowledgeId == null || knowledgeId <= 0 || request == null || request.query() == null
                || request.query().isBlank() || request.query().length() > 2000
                || !Boolean.TRUE.equals(request.embeddingConfirmed())) {
            throw new ServiceException("请输入 1～2000 字的问题，并确认调用当前向量模型", 400);
        }
        AiKnowledgeBase knowledge = requireAccessible(knowledgeId);
        if (!"READY".equals(knowledge.getIndexStatus())) {
            throw new ServiceException("知识库索引尚未就绪，请先检查索引状态", 409);
        }
        int topK = request.topK() == null ? Objects.requireNonNullElse(knowledge.getRetrievalTopK(), 5) : request.topK();
        double minScore = request.minScore() == null ? Objects.requireNonNullElse(knowledge.getRetrievalMinScore(), 0.5) : request.minScore();
        if (topK < 1 || topK > 20 || !Double.isFinite(minScore) || minScore < 0 || minScore > 1) {
            throw new ServiceException("返回数量须为 1～20，最低匹配分须为 0～1", 400);
        }
        List<String> related = KnowledgeRetrievalService.validateRelatedPlan(request.relatedDocumentIds(), List.of(), topK);
        if (!related.isEmpty()) retrieval.validateRelatedSources(knowledge, related, List.of(), topK);
        String key = Objects.toString(caller.getTenantId(), "management") + ":" + caller.getUserId();
        begin(key);
        try {
            long started = System.nanoTime();
            AiVectorStoreResolver.VectorContext vector;
            try {
                vector = vectorResolver.resolve(knowledge);
            } catch (RuntimeException e) {
                throw new ServiceException("向量模型暂不可用或与当前索引不兼容，请检查模型配置", 502);
            }
            long prepareMs = (System.nanoTime() - started) / 1_000_000L;
            var found = related.isEmpty() ? retrieval.search(knowledge, vector, request.query().trim(), topK, minScore, List.of())
                    : retrieval.search(knowledge, vector, request.query().trim(), topK, minScore, List.of(), related);
            // 再经过入口的数据范围检查；核心搜索也会检查索引及文档当前状态。
            requireAccessible(knowledgeId);
            List<Hit> results = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (var match : found.matches()) {
                var metadata = match.embedded().metadata();
                String docId = metadata.getString("doc_id");
                if (!seen.add(docId + ":" + match.embedded().text())) continue;
                results.add(new Hit(results.size() + 1, docId, metadata.getString("doc_name"),
                        metadata.getString("chunk_no"), match.score(), match.embedded().text()));
            }
            int deduplicated = found.matches().size() - results.size();
            return new TestResponse(UUID.randomUUID().toString(), knowledgeId, knowledge.getName(),
                    knowledge.getIndexVersion(), related.isEmpty() ? "VECTOR_V1" : "EXPLICIT_RELATED_V1", results.isEmpty() ? "EMPTY" : "FOUND",
                    topK, minScore, found.candidateCount(), found.filteredCount(), deduplicated,
                    prepareMs, found.embeddingMs(), found.searchMs(),
                    (System.nanoTime() - started) / 1_000_000L, List.copyOf(results),
                    new RelatedCoverage(related.size(), related.size() - found.missingRelatedDocumentIds().size(),
                            found.missingRelatedDocumentIds(), found.vectorQueryCount(), false));
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            // 提供器异常可能含问题/正文、端点或凭证，不向响应或普通日志复制。
            throw new ServiceException("向量模型或检索服务暂不可用，请检查模型和索引后重试", 502);
        } finally {
            end(key);
        }
    }

    private AiKnowledgeBase requireAccessible(Long id) {
        AiKnowledgeBase knowledge = knowledgeService.selectAccessibleKnowledgeBaseById(id);
        if (knowledge == null) throw new ServiceException("知识库不存在或无权访问", 404);
        CallerContext caller = CallerContextHolder.require();
        if (!caller.isSuperAdmin() && !Objects.equals(caller.getTenantId(),
                knowledge.getTenantId() == null ? null : knowledge.getTenantId().toString())) {
            throw new ServiceException("知识库不存在或无权访问", 404);
        }
        return knowledge;
    }

    private synchronized void begin(String key) {
        long now = System.nanoTime();
        recent.entrySet().removeIf(entry -> now - entry.getValue() >= 5_000_000_000L);
        if (active.contains(key) || active.size() >= 8 || recent.containsKey(key) || recent.size() >= 4096) {
            throw new ServiceException("检索测试正在进行或请求过于频繁，请稍后重试", 429);
        }
        active.add(key);
        recent.put(key, now);
    }

    private synchronized void end(String key) { active.remove(key); }

    public Map<String, Object> capabilities(Long id) {
        CallerContext caller = CallerContextHolder.get();
        if (caller == null || caller.getUserId() == null || caller.getUserId() <= 0) throw new ServiceException("请登录后使用", 401);
        AiKnowledgeBase kb = requireAccessible(id);
        return Map.of("version", 1, "strategy", "EXPLICIT_RELATED_V1", "maxRelatedDocuments", 3,
                "maxVectorQueries", 4, "queryEmbeddings", 1, "indexReady", "READY".equals(kb.getIndexStatus()),
                "defaultResultLimit", Objects.requireNonNullElse(kb.getRetrievalTopK(), 5), "evidenceSupportVerified", false);
    }

    public record TestRequest(String query, Integer topK, Double minScore, Boolean embeddingConfirmed, List<String> relatedDocumentIds) {
        public TestRequest(String query, Integer topK, Double minScore, Boolean embeddingConfirmed) {
            this(query, topK, minScore, embeddingConfirmed, List.of());
        }
    }
    public record RelatedCoverage(int requestedDocuments, int matchedDocuments, List<String> missingDocumentIds,
                                  int vectorQueryCount, boolean evidenceSupportVerified) { }
    public record Hit(int rank, String documentId, String documentName, String chunkNo, double score, String content) { }
    public record TestResponse(String traceId, Long knowledgeId, String knowledgeName, Long indexVersion,
                               String strategy, String state, int topK, double minScore, int candidateCount,
                               int filteredCount, int deduplicatedCount, long prepareMs, long embeddingMs,
                               long searchMs, long durationMs, List<Hit> results, RelatedCoverage relatedCoverage) { }
}
