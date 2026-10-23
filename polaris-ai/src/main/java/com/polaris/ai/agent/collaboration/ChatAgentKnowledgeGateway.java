package com.polaris.ai.agent.collaboration;

import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.dto.KnowledgeCitation;
import com.polaris.ai.knowledge.KnowledgeRetrievalService;
import com.polaris.ai.rag.AiVectorStoreResolver;
import com.polaris.ai.service.IAiKnowledgeService;

import java.util.*;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.Scope;
import static com.polaris.ai.agent.collaboration.BoundedAgentContracts.*;

/** 单请求证据适配器；直接调用共享检索内核，不构造工作流节点，不重放检索。 */
public final class ChatAgentKnowledgeGateway implements AgentCollaborationPorts.KnowledgeGateway {
    private final Scope scope;
    private final Long knowledgeId;
    private final String knowledgeHash,retrievalContext;
    private final AgentKnowledgeSnapshotService snapshots;
    private final IAiKnowledgeService knowledge;
    private final AiVectorStoreResolver vectors;
    private final KnowledgeRetrievalService retrieval;
    private final AgentToolCatalog actors;
    private AiKnowledgeBase selected;
    private List<KnowledgeCitation> citations=List.of();
    public ChatAgentKnowledgeGateway(Scope scope,Long knowledgeId,String knowledgeHash,String retrievalContext,
            AgentKnowledgeSnapshotService snapshots,IAiKnowledgeService knowledge,AiVectorStoreResolver vectors,KnowledgeRetrievalService retrieval,AgentToolCatalog actors) {
        this.scope=scope;this.knowledgeId=knowledgeId;this.knowledgeHash=knowledgeHash;this.retrievalContext=retrievalContext;
        this.snapshots=snapshots;this.knowledge=knowledge;this.vectors=vectors;this.retrieval=retrieval;this.actors=actors;
    }
    public List<KnowledgeCitation> citations() {return citations;}
    public void checkCurrent() {
        if(knowledgeId==null)return;
        try {
            snapshots.requireCurrent(scope.tenantId(),scope.actorUserId(),knowledgeId.toString(),knowledgeHash);
            if(selected!=null)asActor(()-> {retrieval.validateSources(selected,citations.stream().map(KnowledgeCitation::documentId).distinct().toList());return null;});
        } catch(Failure safe) {throw safe;}catch(RuntimeException unavailable) {throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);}
    }
    @Override public List<KnowledgeEvidence> retrieve(Request request,AgentCallControl control) {
        if(!(request.context().entry() instanceof AgentEntryIdentity.Chat) || knowledgeId==null || !request.knowledgeRequired())
            throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);
        try {
            control.check();checkCurrent();selected=knowledge.selectAccessibleKnowledgeBaseById(knowledgeId);
            if(selected==null || !scope.tenantId().equals(selected.getTenantId()))throw new Failure(Code.ACCESS_DENIED);
            String query=retrievalContext.isBlank()?request.question():"前文问题：\n"+retrievalContext+"\n本轮问题：\n"+request.question();
            var found=retrieval.search(selected,vectors.resolve(selected),query,20,.5,List.of(),List.of(),control::check);
            List<KnowledgeEvidence> evidence=new ArrayList<>();List<KnowledgeCitation> refs=new ArrayList<>();
            Set<String> seen=new HashSet<>();Map<Long,Integer> perDocument=new HashMap<>();int available=8000;
            for(var match:found.matches()) {
                control.check();if(evidence.size()==5 || available==0)break;
                if(match==null || match.embedded()==null || match.embedded().text().isBlank() || match.embedded().metadata()==null)continue;
                String id=match.embedded().metadata().getString("doc_id"),text=match.embedded().text();
                if(id==null || !id.matches("[1-9][0-9]{0,18}"))continue;Long document=Long.valueOf(id);
                if(perDocument.getOrDefault(document,0)>=3 || !seen.add(id+":"+AgentBindingService.digest(text)))continue;
                int length=Math.min(text.length(),Math.min(4000,available));
                if(length<text.length() && Character.isHighSurrogate(text.charAt(length-1)))length--;
                if(length==0)break;String citation="S"+(evidence.size()+1);
                String chunk=Objects.toString(match.embedded().metadata().toMap().get("chunk_no"),"");
                evidence.add(new KnowledgeEvidence(citation,text.substring(0,length)));
                refs.add(new KnowledgeCitation(citation,knowledgeId,document,selected.getIndexVersion(),
                        AgentBindingService.digest(selected.getVectorCollection()),chunk.matches("[0-9]{1,9}")?chunk:null,
                        AgentBindingService.digest(text),length,length<text.length()));
                perDocument.merge(document,1,Integer::sum);available-=length;
            }
            citations=List.copyOf(refs);checkCurrent();control.check();return List.copyOf(evidence);
        } catch(Failure safe) {throw safe;} catch(RuntimeException unavailable) {throw new Failure(Code.KNOWLEDGE_UNAVAILABLE);}
    }
    @Override public void recheck(Context context,List<KnowledgeEvidence> evidence) {checkCurrent();}
    private <T>T asActor(java.util.function.Supplier<T> action) {
        var previous=com.polaris.ai.core.context.CallerContextHolder.get();
        try {com.polaris.ai.core.context.CallerContextHolder.set(actors.requireActor(scope.tenantId(),scope.actorUserId()));return action.get();}
        finally {com.polaris.ai.core.context.CallerContextHolder.set(previous);}
    }
}
