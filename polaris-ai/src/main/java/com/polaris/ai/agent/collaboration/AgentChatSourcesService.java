package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.dto.RecognizedDoc;
import com.polaris.ai.knowledge.ChatKnowledgeEvidenceService;
import com.polaris.ai.mapper.AgentChatMapper;
import com.polaris.ai.mapper.AiChatMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;

/** 刷新后的来源只读恢复；当前授权的名称/片段指针和原工具查询时间，不重新调用上游。 */
@Service
public class AgentChatSourcesService {
    public record HistoricalSource(HistoricalReference reference,boolean available,List<RecognizedDoc> knowledge,ChatToolSourceRow tool) { }
    public record View(String runId,List<RecognizedDoc> knowledge,List<ChatToolSourceRow> tools,List<HistoricalSource> history) { }
    private final AgentChatMapper chats;
    private final AiChatMapper messages;
    private final AgentToolCatalog actors;
    private final ChatKnowledgeEvidenceService knowledge;
    private final AgentChatSourceGuard guard;
    public AgentChatSourcesService(AgentChatMapper chats,AiChatMapper messages,AgentToolCatalog actors,ChatKnowledgeEvidenceService knowledge,AgentChatSourceGuard guard) {
        this.chats=chats;this.messages=messages;this.actors=actors;this.knowledge=knowledge;this.guard=guard;
    }
    public View get(Long conversationId,Long messageId) {
        var caller=CallerContextHolder.require();
        if(!caller.isPlatformMode() || !caller.isTenantAdmin())throw denied();
        var scope=new Scope(Long.valueOf(caller.getTenantId()),caller.getUserId(),conversationId);actors.requireActor(scope.tenantId(),scope.actorUserId());
        var row=chats.message(scope,messageId);if(!safe(row))throw denied();
        Dependencies refs;
        try {refs=AgentPlanValidator.JSON.readValue(row.getHistoryDependencies(),Dependencies.class);if(refs==null)throw conflict();}
        catch(RuntimeException invalid) {throw conflict();}
        List<HistoricalSource> historical=new ArrayList<>();
        for(var ref:refs.history()) {
            var origin=chats.message(scope,ref.messageId());
            if(!safe(origin) || !row.getContent().contains("[H"+ref.messageId()+":"+ref.citationId()+"]")) {
                historical.add(new HistoricalSource(ref,false,List.of(),null));continue;
            }
            if(ref.citationId().startsWith("S")) {
                Dependencies original;
                try {original=AgentPlanValidator.JSON.readValue(origin.getHistoryDependencies(),Dependencies.class);}
                catch(RuntimeException invalid) {original=null;}
                if(original==null || !guard.current(scope,original)) {historical.add(new HistoricalSource(ref,false,List.of(),null));continue;}
                var found=knowledge(conversationId,ref.messageId()).stream().filter(doc->doc.getCitationIds().contains(ref.citationId())).toList();
                historical.add(new HistoricalSource(ref,!found.isEmpty(),found,null));
            } else {
                int no=Integer.parseInt(ref.citationId().substring(1));
                var tool=chats.toolSources(scope,origin.getAgentRunId()).stream().filter(t->Objects.equals(t.getCallNo(),no)).findFirst().orElse(null);
                historical.add(new HistoricalSource(ref,tool!=null,List.of(),tool));
            }
        }
        // 读取后重查原回答，删除/审核或引用变化时不返回旧快照。
        var current=chats.message(scope,messageId);
        if(!safe(current) || !Objects.equals(current.getContent(),row.getContent()) || !Objects.equals(current.getHistoryDependencies(),row.getHistoryDependencies()))throw conflict();
        return new View(row.getAgentRunId(),knowledge(conversationId,messageId),chats.toolSources(scope,row.getAgentRunId()),List.copyOf(historical));
    }
    private List<RecognizedDoc> knowledge(Long conversation,Long id) {
        var message=messages.selectMessageByIdAndConversation(id,conversation);
        if(message==null || !"assistant".equals(message.getRole()) || !"SAFE".equals(message.getModerationStatus()))return List.of();
        knowledge.restoreHistory(List.of(message));return message.getAttachedDocs();
    }
    private boolean safe(AgentChatHistoryRow row) {return row!=null && "assistant".equals(row.getRole()) && "SAFE".equals(row.getModerationStatus()) && "SUCCEEDED".equals(row.getGenerationStatus());}
}
