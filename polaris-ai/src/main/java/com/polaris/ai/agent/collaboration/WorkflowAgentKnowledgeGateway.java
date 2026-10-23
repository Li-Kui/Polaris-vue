package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.knowledge.KnowledgeRetrievalService;
import com.polaris.ai.workflow.node.WorkflowKnowledgeResourceHandle;
import com.polaris.ai.workflow.spi.WorkflowNodeContext;

import java.util.*;
import java.util.function.Supplier;

import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 每个节点独占的临时证据包；直接使用检索内核，不伪造知识节点上下文。 */
public final class WorkflowAgentKnowledgeGateway implements AgentCollaborationPorts.KnowledgeGateway {
    private final WorkflowNodeContext node;
    private final AgentToolCatalog actors;
    private final AgentKnowledgeSnapshotService snapshots;
    private final KnowledgeRetrievalService retrieval;
    private final WorkflowKnowledgeResourceHandle handle;
    private List<Long> documents = List.of();
    private List<KnowledgeSource> sources = List.of();
    public WorkflowAgentKnowledgeGateway(WorkflowNodeContext node, AgentToolCatalog actors,
            AgentKnowledgeSnapshotService snapshots, KnowledgeRetrievalService retrieval) {
        this.node = node; this.actors = actors; this.snapshots = snapshots; this.retrieval = retrieval;
        var selected = node.resources().values().stream().filter(resource -> "KNOWLEDGE_BASE".equals(resource.kind())).toList();
        if (selected.size() > 1 || node.config().has("knowledgeId") != (selected.size() == 1)) throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);
        handle = selected.isEmpty() ? null : selected.get(0).handle() instanceof WorkflowKnowledgeResourceHandle value ? value : null;
        if (!selected.isEmpty() && (handle == null || !node.tenantId().equals(handle.knowledge().getTenantId())
                || !node.config().path("knowledgeId").asString().equals(selected.get(0).resourceId())
                || !selected.get(0).resourceId().equals(handle.knowledge().getId().toString()))) throw new Failure(Code.ACCESS_DENIED);
    }
    public record KnowledgeSource(String citationId, String knowledgeId, String documentId, String indexVersion, String chunkNo, boolean truncated) { }
    public List<KnowledgeSource> sources() { return sources; }
    private Long actorId() { return Long.valueOf(node.principalId()); }
    private <T> T asActor(Supplier<T> action) {
        var actor = actors.requireActor(node.tenantId(), actorId()); var previous = CallerContextHolder.get();
        try { CallerContextHolder.set(actor); return action.get(); }
        finally { CallerContextHolder.set(previous); }
    }
    public void checkCurrent() {
        if (node.cancellation().isCancellationRequested() || Thread.currentThread().isInterrupted()) throw new Failure(Code.CANCELLED);
        if (handle == null) return;
        snapshots.requireCurrent(node.tenantId(), actorId(), node.config().path("knowledgeId").asString(), node.config().path("knowledgeHash").asString());
        try { asActor(() -> {retrieval.validateSources(handle.knowledge(), documents); return null;}); }
        catch (Failure safe) { throw safe; }
        catch (RuntimeException unavailable) { throw new Failure(Code.KNOWLEDGE_UNAVAILABLE); }
    }
    @Override public List<KnowledgeEvidence> retrieve(Request request, AgentCallControl control) {
        if (handle == null || !request.knowledgeRequired() || !node.executionId().equals(request.context().executionId())
                || !node.nodeRunId().equals(request.context().nodeRunId())) throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);
        checkCurrent(); control.check();
        try {
            var found = asActor(() -> retrieval.search(handle.knowledge(), handle.vectorContext(), request.question(),
                    20, .5, List.of(), List.of(), control::check));
            List<KnowledgeEvidence> evidence = new ArrayList<>(); List<KnowledgeSource> metadata = new ArrayList<>();
            Map<String, Integer> perDocument = new HashMap<>(); Set<String> seen = new HashSet<>(); int available = 8000;
            for (var match : found.matches()) {
                control.check();
                if (evidence.size() == 5 || available == 0) break;
                if (match == null || match.embedded() == null || match.embedded().text().isBlank() || match.embedded().metadata() == null) continue;
                String document = match.embedded().metadata().getString("doc_id"), content = match.embedded().text();
                if (document == null || !document.matches("[1-9][0-9]{0,18}") || perDocument.getOrDefault(document, 0) >= 3
                        || !seen.add(document + ":" + AgentBindingService.digest(content))) continue;
                int length = Math.min(content.length(), available);
                if (length < content.length() && length > 0 && Character.isHighSurrogate(content.charAt(length - 1))) length--;
                if (length == 0) break;
                String citation = "S" + (evidence.size() + 1);
                evidence.add(new KnowledgeEvidence(citation, content.substring(0, length)));
                String chunk = Objects.toString(match.embedded().metadata().toMap().get("chunk_no"), "");
                metadata.add(new KnowledgeSource(citation, handle.knowledge().getId().toString(), document,
                        handle.knowledge().getIndexVersion().toString(), chunk.matches("[0-9]{1,9}") ? chunk : "", length < content.length()));
                perDocument.merge(document, 1, Integer::sum); available -= length;
            }
            sources = List.copyOf(metadata); documents = sources.stream().map(source -> Long.valueOf(source.documentId())).distinct().toList();
            checkCurrent(); control.check(); return List.copyOf(evidence);
        } catch (Failure safe) { throw safe; }
        catch (RuntimeException unavailable) { throw new Failure(Code.KNOWLEDGE_UNAVAILABLE); }
    }
    @Override public void recheck(Context context, List<KnowledgeEvidence> evidence) { checkCurrent(); }
}
