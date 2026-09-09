package com.polaris.ai.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polaris.ai.workflow.node.WorkflowKnowledgeResourceHandle;
import com.polaris.ai.workflow.spi.ResolvedWorkflowResource;
import com.polaris.ai.workflow.spi.WorkflowNodeContext;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/** 知识库节点的检索编排服务；节点处理器不直接依赖具体向量库查询细节。 */
@Service
public class WorkflowKnowledgeRetrievalService {

    private static final int MAX_RESULT_LIMIT = 20;
    private static final int MAX_CANDIDATE_LIMIT = 50;

    private final ObjectMapper objectMapper;

    public WorkflowKnowledgeRetrievalService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode retrieve(WorkflowNodeContext context) {
        long startedAt = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        String query = requiredQuery(context.input());
        String searchText = searchText(context.input(), query);
        List<String> documentIds = documentIds(context.input());
        JsonNode config = context.config() == null ? objectMapper.createObjectNode() : context.config();
        String mode = retrievalMode(config);
        int resultLimit = bounded(config.path("resultLimit").asInt(5), 1, MAX_RESULT_LIMIT);
        int candidateLimit = bounded(Math.max(resultLimit * 4, 12), resultLimit, MAX_CANDIDATE_LIMIT);
        int maxChunksPerDocument = bounded(config.path("maxChunksPerDocument").asInt(3), 1, 10);
        int maxContextTokens = contextTokenBudget(config);
        double minScore = minimumScore(mode);
        boolean allowDegradation = "ALLOW".equalsIgnoreCase(
                config.path("degradationPolicy").asText("DENY"));

        List<ResolvedWorkflowResource> resources = context.resources().values().stream()
                .filter(resource -> "KNOWLEDGE_BASE".equals(resource.kind()))
                .sorted(Comparator.comparing(ResolvedWorkflowResource::key))
                .toList();
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个可用的知识库");
        }

        List<Candidate> candidates = new ArrayList<>();
        Map<String, String> indexRevisions = new LinkedHashMap<>();
        List<SourceFailure> sourceFailures = new ArrayList<>();
        int successfulSourceCount = 0;
        for (ResolvedWorkflowResource resource : resources) {
            context.cancellation().throwIfCancellationRequested();
            if (!(resource.handle() instanceof WorkflowKnowledgeResourceHandle handle)) {
                throw new IllegalArgumentException("知识库资源无法用于检索: " + resource.key());
            }
            try {
                var embedding = handle.vectorContext().embeddingModel().embed(searchText).content();
                Filter filter = metadataKey("knowledge_base_id")
                        .isEqualTo(handle.knowledge().getId().toString());
                if (!documentIds.isEmpty()) {
                    filter = filter.and(metadataKey("doc_id").isIn(documentIds));
                }
                EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(candidateLimit)
                        .minScore(minScore)
                        .filter(filter)
                        .build();
                List<EmbeddingMatch<TextSegment>> matches = handle.vectorContext()
                        .embeddingStore().search(request).matches();
                if (matches != null) {
                    for (EmbeddingMatch<TextSegment> match : matches) {
                        if (match == null || match.embedded() == null
                                || match.score() == null
                                || !Double.isFinite(match.score())
                                || match.embedded().text() == null
                                || match.embedded().text().isBlank()) continue;
                        candidates.add(candidate(resource, handle, match));
                    }
                }
                successfulSourceCount++;
            } catch (RuntimeException error) {
                if (!allowDegradation) throw error;
                sourceFailures.add(new SourceFailure(resource.key(), "SOURCE_UNAVAILABLE"));
            }
            Object revision = resource.attributes().get("indexVersion");
            if (revision != null) indexRevisions.put(resource.key(), String.valueOf(revision));
        }
        if (successfulSourceCount == 0 && !sourceFailures.isEmpty()) {
            throw new IllegalStateException("所选知识库当前都无法检索，请稍后重试");
        }

        Selection selection = selectCandidates(
                candidates, resultLimit, maxChunksPerDocument, maxContextTokens);
        List<Candidate> selected = selection.candidates();
        if (selected.isEmpty() && "FAIL".equalsIgnoreCase(
                config.path("emptyPolicy").asText("CONTINUE"))) {
            throw new IllegalStateException("没有找到符合条件的知识库资料");
        }
        return output(context, query, mode, traceId, resources.size(), successfulSourceCount,
                indexRevisions, sourceFailures, selected, selection.truncationReasons(),
                maxContextTokens, elapsedMillis(startedAt));
    }

    private Candidate candidate(
            ResolvedWorkflowResource resource,
            WorkflowKnowledgeResourceHandle handle,
            EmbeddingMatch<TextSegment> match) {
        var metadata = match.embedded().metadata();
        String documentId = metadata == null ? "" : text(metadata.getString("doc_id"));
        String documentName = metadata == null ? "" : text(metadata.getString("doc_name"));
        String chunkNo = metadata == null ? "" : text(metadata.getString("chunk_no"));
        String knowledgeId = String.valueOf(handle.knowledge().getId());
        if (documentId.isBlank()) {
            documentId = "document-" + digest(documentName + ":" + match.embedded().text())
                    .substring(0, 12);
        }
        String stableKey = knowledgeId + ":" + documentId + ":" + chunkNo + ":"
                + digest(match.embedded().text());
        return new Candidate(
                stableKey, bounded(match.score(), 0, 1), match.embedded().text(), resource.key(),
                knowledgeId, text(handle.knowledge().getName()),
                documentId, documentName, chunkNo,
                handle.knowledge().getIndexVersion() == null
                        ? "" : String.valueOf(handle.knowledge().getIndexVersion()));
    }

    private Selection selectCandidates(
            List<Candidate> candidates,
            int resultLimit,
            int maxChunksPerDocument,
            int maxContextTokens) {
        Map<String, Candidate> unique = new LinkedHashMap<>();
        candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .forEach(candidate -> unique.putIfAbsent(
                        candidate.knowledgeSourceId() + ":" + candidate.documentId() + ":"
                                + digest(candidate.content()), candidate));
        Map<String, Integer> documentCounts = new HashMap<>();
        List<Candidate> selected = new ArrayList<>();
        int usedTokens = 0;
        Set<String> truncationReasons = new LinkedHashSet<>();
        for (Candidate candidate : unique.values()) {
            if (selected.size() >= resultLimit) {
                truncationReasons.add("RESULT_LIMIT");
                break;
            }
            String documentKey = candidate.knowledgeSourceId() + ":" + candidate.documentId();
            if (documentCounts.getOrDefault(documentKey, 0) >= maxChunksPerDocument) {
                truncationReasons.add("DOCUMENT_LIMIT");
                continue;
            }
            int candidateTokens = estimateTokens(candidate.content());
            if (usedTokens + candidateTokens > maxContextTokens) {
                truncationReasons.add("CONTEXT_BUDGET");
                if (!selected.isEmpty()) continue;
                Candidate shortened = withContent(candidate,
                        candidate.content().substring(0,
                                Math.min(candidate.content().length(), maxContextTokens * 3)));
                selected.add(shortened);
                documentCounts.merge(documentKey, 1, Integer::sum);
                break;
            }
            selected.add(candidate);
            usedTokens += candidateTokens;
            documentCounts.merge(documentKey, 1, Integer::sum);
        }
        return new Selection(List.copyOf(selected), List.copyOf(truncationReasons));
    }

    private static Candidate withContent(Candidate candidate, String content) {
        return new Candidate(candidate.id(), candidate.score(), content, candidate.resourceKey(),
                candidate.knowledgeSourceId(), candidate.knowledgeSourceName(),
                candidate.documentId(), candidate.documentName(), candidate.chunkNo(),
                candidate.indexRevision());
    }

    private ObjectNode output(
            WorkflowNodeContext context,
            String query,
            String mode,
            String traceId,
            int sourceCount,
            int successfulSourceCount,
            Map<String, String> indexRevisions,
            List<SourceFailure> sourceFailures,
            List<Candidate> selected,
            List<String> truncationReasons,
            int contextBudgetTokens,
            long durationMs) {
        ArrayNode results = objectMapper.createArrayNode();
        ArrayNode contextItems = objectMapper.createArrayNode();
        int rank = 0;
        for (Candidate candidate : selected) {
            rank++;
            String resultId = "result-" + digest(candidate.id()).substring(0, 12);
            String citationId = "S" + rank;
            ObjectNode item = results.addObject();
            item.put("id", resultId);
            item.put("rank", rank);
            item.put("relevanceLevel", relevanceLevel(candidate.score()));
            item.put("score", candidate.score());
            item.put("content", candidate.content());
            ObjectNode source = item.putObject("source");
            source.put("resourceKey", candidate.resourceKey());
            source.put("knowledgeSourceId", candidate.knowledgeSourceId());
            source.put("knowledgeSourceName", candidate.knowledgeSourceName());
            source.put("indexRevision", candidate.indexRevision());
            ObjectNode document = item.putObject("document");
            document.put("documentId", candidate.documentId());
            document.put("documentVersionId", candidate.indexRevision());
            document.put("documentName", candidate.documentName());
            ObjectNode location = item.putObject("location");
            if (!candidate.chunkNo().isBlank()) location.put("chunkNo", candidate.chunkNo());
            ObjectNode citation = item.putObject("citation");
            citation.put("id", citationId);
            citation.put("label", candidate.documentName().isBlank()
                    ? "知识库资料 " + rank : candidate.documentName());
            ObjectNode contextItem = contextItems.addObject();
            contextItem.put("resultId", resultId);
            contextItem.put("citationId", citationId);
            contextItem.put("content", candidate.content());
            contextItem.put("knowledgeSourceName", candidate.knowledgeSourceName());
            contextItem.put("documentName", candidate.documentName());
        }

        ObjectNode output = objectMapper.createObjectNode();
        boolean degraded = !sourceFailures.isEmpty();
        output.put("state", degraded ? "PARTIAL" : selected.isEmpty() ? "EMPTY" : "FOUND");
        output.put("hasResults", !selected.isEmpty());
        output.put("complete", !degraded);
        output.put("degraded", degraded);
        output.put("resultCount", selected.size());
        output.set("results", results);
        ObjectNode knowledgeContext = output.putObject("knowledgeContext");
        knowledgeContext.put("type", "UNTRUSTED_KNOWLEDGE_CONTEXT");
        knowledgeContext.put("usagePolicy", "CONTENT_ONLY");
        knowledgeContext.set("items", contextItems);
        knowledgeContext.put("tokenCount", estimatedTokens(selected));
        knowledgeContext.put("budgetTokens", contextBudgetTokens);
        knowledgeContext.put("truncated", !truncationReasons.isEmpty());
        ArrayNode reasons = knowledgeContext.putArray("truncationReasons");
        truncationReasons.forEach(reasons::add);
        knowledgeContext.put("contextHandle", context.executionId() + ":" + context.nodeRunId());
        ObjectNode retrievalInfo = output.putObject("retrievalInfo");
        retrievalInfo.put("traceId", traceId);
        retrievalInfo.put("query", query);
        ObjectNode policy = retrievalInfo.putObject("policy");
        policy.put("code", mode);
        policy.put("version", 1);
        retrievalInfo.put("sourceCount", sourceCount);
        retrievalInfo.put("successfulSourceCount", successfulSourceCount);
        ArrayNode failures = retrievalInfo.putArray("failedSources");
        sourceFailures.forEach(failure -> failures.addObject()
                .put("resourceKey", failure.resourceKey())
                .put("reasonCode", failure.reasonCode()));
        retrievalInfo.set("indexRevisions", objectMapper.valueToTree(indexRevisions));
        retrievalInfo.put("durationMs", durationMs);
        retrievalInfo.put("truncated", !truncationReasons.isEmpty());
        retrievalInfo.set("truncationReasons", reasons.deepCopy());
        retrievalInfo.put("retryCount", Math.max(0, context.attemptNo() - 1));
        retrievalInfo.put("executedAt", Instant.now().toString());
        return output;
    }

    private static String requiredQuery(JsonNode input) {
        String query = input == null ? "" : input.path("query").asText("").trim();
        if (query.isBlank()) throw new IllegalArgumentException("请选择要检索的内容");
        if (query.length() > 4000) throw new IllegalArgumentException("检索内容不能超过4000个字符");
        return query;
    }

    private static String searchText(JsonNode input, String query) {
        String context = input == null ? "" : input.path("searchContext").asText("").trim();
        if (context.length() > 8000) {
            throw new IllegalArgumentException("补充背景不能超过8000个字符");
        }
        return context.isBlank() ? query : query + "\n补充背景：" + context;
    }

    private static List<String> documentIds(JsonNode input) {
        JsonNode filters = input == null ? null : input.get("filters");
        if (filters == null || filters.isNull()) return List.of();
        if (!filters.isObject()) throw new IllegalArgumentException("过滤条件必须是对象");
        filters.fieldNames().forEachRemaining(field -> {
            if (!"documentIds".equals(field)) {
                throw new IllegalArgumentException("不支持的过滤条件: " + field);
            }
        });
        JsonNode values = filters.get("documentIds");
        if (values == null || values.isNull()) return List.of();
        if (!values.isArray()) throw new IllegalArgumentException("文档范围必须是数组");
        if (values.size() > 100) throw new IllegalArgumentException("文档范围最多包含100项");
        List<String> result = new ArrayList<>();
        values.forEach(value -> {
            String documentId = value.asText("").trim();
            if (!documentId.isBlank() && !result.contains(documentId)) result.add(documentId);
        });
        return List.copyOf(result);
    }

    private static String retrievalMode(JsonNode config) {
        String mode = config == null ? "BALANCED" : config.path("retrievalMode").asText("BALANCED");
        return switch (mode.toUpperCase()) {
            case "PRECISE", "BROAD" -> mode.toUpperCase();
            default -> "BALANCED";
        };
    }

    private static int contextTokenBudget(JsonNode config) {
        String mode = config == null ? "AUTO" : config.path("contextBudgetMode").asText("AUTO");
        if (!"MANUAL".equalsIgnoreCase(mode)) return 4000;
        return bounded(config.path("maxContextTokens").asInt(4000), 256, 32000);
    }

    private static double minimumScore(String mode) {
        return switch (mode) {
            case "PRECISE" -> 0.65;
            case "BROAD" -> 0.30;
            default -> 0.50;
        };
    }

    private static String relevanceLevel(double score) {
        if (score >= 0.75) return "HIGH";
        if (score >= 0.55) return "MEDIUM";
        return "LOW";
    }

    private static int estimatedTokens(List<Candidate> candidates) {
        return candidates.stream().mapToInt(candidate -> estimateTokens(candidate.content())).sum();
    }

    private static int estimateTokens(String value) {
        return Math.max(1, (int) Math.ceil(text(value).length() / 3.0));
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static double bounded(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(text(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) result.append(String.format("%02x", valueByte));
            return result.toString();
        } catch (Exception ignored) {
            return String.format("%064x", Integer.toUnsignedLong(text(value).hashCode()));
        }
    }

    private record Candidate(
            String id,
            double score,
            String content,
            String resourceKey,
            String knowledgeSourceId,
            String knowledgeSourceName,
            String documentId,
            String documentName,
            String chunkNo,
            String indexRevision) {
    }

    private record SourceFailure(String resourceKey, String reasonCode) {
    }

    private record Selection(List<Candidate> candidates, List<String> truncationReasons) {
    }
}
