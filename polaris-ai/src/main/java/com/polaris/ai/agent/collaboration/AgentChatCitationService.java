package com.polaris.ai.agent.collaboration;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.knowledge.ChatKnowledgeEvidenceService;
import com.polaris.ai.mapper.AgentChatMapper;
import org.springframework.stereotype.Service;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.*;

/** 历史引用必须由当前回答实际存档指针指向，再沿原消息知识预览重新鉴权；无检索。 */
@Service
public class AgentChatCitationService {
    private final AgentChatMapper chats;private final AgentChatSourceGuard sources;private final ChatKnowledgeEvidenceService evidence;
    public AgentChatCitationService(AgentChatMapper chats,AgentChatSourceGuard sources,ChatKnowledgeEvidenceService evidence) {this.chats=chats;this.sources=sources;this.evidence=evidence;}
    public ChatKnowledgeEvidenceService.Preview preview(Long conversationId,Long messageId,Long originId,String citation) {
        var actor=CallerContextHolder.require();
        if(!actor.isPlatformMode() || actor.getUserId()==null || citation==null || !citation.matches("S[1-9][0-9]{0,2}"))throw denied();
        var scope=new Scope(Long.valueOf(actor.getTenantId()),actor.getUserId(),conversationId);
        var row=chats.message(scope,messageId);
        if(row==null || !"assistant".equals(row.getRole()) || !"SAFE".equals(row.getModerationStatus())
                || !"SUCCEEDED".equals(row.getGenerationStatus()) || row.getHistoryDependencies()==null
                || !row.getContent().contains("[H"+originId+":"+citation+"]"))throw denied();
        Dependencies refs;
        try {refs=AgentPlanValidator.JSON.readValue(row.getHistoryDependencies(),Dependencies.class);}
        catch(RuntimeException invalid) {throw conflict();}
        if(!refs.history().contains(new HistoricalReference(originId,citation)) || !sources.current(scope,refs))throw denied();
        return evidence.preview(conversationId,originId,citation);
    }
}
