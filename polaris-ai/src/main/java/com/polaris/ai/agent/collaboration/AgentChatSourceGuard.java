package com.polaris.ai.agent.collaboration;

import com.polaris.ai.mapper.AgentChatMapper;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.mapper.AiKnowledgeMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.polaris.ai.agent.collaboration.AgentChatContracts.Dependencies;
import static com.polaris.ai.agent.collaboration.AgentChatContracts.Scope;

/** 历史来源只查本地当前元数据；不重检索、不读取片段或请求远端工具。 */
@Service
public class AgentChatSourceGuard {
    private final AiKnowledgeMapper knowledge;
    private final AiDocumentMapper documents;
    private final AgentChatMapper chats;
    public AgentChatSourceGuard(AiKnowledgeMapper knowledge, AiDocumentMapper documents, AgentChatMapper chats) {
        this.knowledge = knowledge; this.documents = documents; this.chats = chats;
    }
    public boolean current(Scope scope, Dependencies dependencies) {
        for (var ref : dependencies.knowledge()) {
            var kb = knowledge.selectWorkflowResource(scope.tenantId(), ref.knowledgeId());
            var doc = documents.selectById(ref.documentId());
            if (kb == null || doc == null || !Objects.equals(scope.tenantId(), kb.getTenantId())
                    || !Objects.equals(doc.getKnowledgeBaseId(), kb.getId())
                    || !"0".equals(kb.getDelFlag()) || !"READY".equals(kb.getIndexStatus())
                    || !"0".equals(doc.getDelFlag()) || !"2".equals(doc.getStatus()) || !"SAFE".equals(doc.getModerationStatus())
                    || !Objects.equals(ref.indexVersion(), kb.getIndexVersion()) || kb.getVectorCollection() == null
                    || !Objects.equals(ref.collectionHash(), AgentBindingService.digest(kb.getVectorCollection()))) return false;
        }
        for (var ref : dependencies.tools()) if (chats.toolSource(scope, ref) != 1) return false;
        for(var ref:dependencies.history()) {
            var original=chats.message(scope,ref.messageId());
            if(original==null || !"assistant".equals(original.getRole()) || !"SAFE".equals(original.getModerationStatus())
                    || !"SUCCEEDED".equals(original.getGenerationStatus()))return false;
        }
        return true;
    }
}
