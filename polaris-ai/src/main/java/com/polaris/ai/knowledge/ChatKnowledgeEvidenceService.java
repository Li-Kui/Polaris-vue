package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.domain.AiMessage;
import com.polaris.ai.dto.KnowledgeCitation;
import com.polaris.ai.dto.RecognizedDoc;
import com.polaris.ai.mapper.AiChatMapper;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.service.IAiKnowledgeService;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** 引用是消息的片段指针，不是独立授权；只读取仍有效的原索引快照，不自动回退新版。 */
@Service
public class ChatKnowledgeEvidenceService {
    private final AiChatMapper chats;
    private final AiDocumentMapper documents;
    private final IAiKnowledgeService knowledge;
    private final KnowledgeDocumentSnapshotStore snapshots;
    private final ObjectMapper json;

    public ChatKnowledgeEvidenceService(AiChatMapper chats, AiDocumentMapper documents, IAiKnowledgeService knowledge,
                                        KnowledgeDocumentSnapshotStore snapshots, ObjectMapper json) {
        this.chats=chats; this.documents=documents; this.knowledge=knowledge; this.snapshots=snapshots; this.json=json;
    }

    public record Archive(int version, List<KnowledgeCitation> citations) { }
    public record Preview(String citationId, Long documentId, String documentName, Long indexVersion,
                          String chunkNo, List<String> locations, String text, boolean truncated,
                          KnowledgeCitationOccurrences.Report occurrences) {
        public Preview(String citationId, Long documentId, String documentName, Long indexVersion,
                       String chunkNo, List<String> locations, String text, boolean truncated) {
            this(citationId, documentId, documentName, indexVersion, chunkNo, locations, text, truncated, null);
        }
    }

    public static String encode(List<KnowledgeCitation> citations, ObjectMapper json) {
        if (citations == null || citations.isEmpty()) return null;
        validate(citations);
        return json.writeValueAsString(new Archive(1, List.copyOf(citations)));
    }

    private List<KnowledgeCitation> decode(String value) {
        if (value == null || value.isBlank()) return List.of();
        if (value.length() > 50000) throw unavailable();
        try {
            Archive archive=json.readValue(value, Archive.class);
            if (archive == null || archive.version()!=1) throw unavailable();
            validate(archive.citations());
            return archive.citations();
        } catch (RuntimeException e) {throw unavailable();}
    }

    private static void validate(List<KnowledgeCitation> citations) {
        if (citations == null || citations.isEmpty() || citations.size()>50) throw unavailable();
        Set<String> ids=new HashSet<>();
        for (var c:citations) {
            if (c==null || c.citationId()==null || !c.citationId().matches("S([1-9]|[1-4][0-9]|50)") || !ids.add(c.citationId())
                    || c.knowledgeBaseId()==null || c.knowledgeBaseId()<=0 || c.documentId()==null || c.documentId()<=0
                    || c.indexVersion()==null || c.indexVersion()<1 || c.collectionHash()==null || !c.collectionHash().matches("[a-f0-9]{64}")
                    || c.chunkHash()==null || !c.chunkHash().matches("[a-f0-9]{64}") || c.includedChars()<1 || c.includedChars()>4000
                    || (c.chunkNo()!=null && !c.chunkNo().matches("[0-9]{1,9}"))) throw unavailable();
        }
    }

    /** 历史只附带重新鉴权后的当前来源名称，不读正文；撤权/隔离后不再返回来源摘要。 */
    public void restoreHistory(List<AiMessage> messages) {
        Map<Long,AiKnowledgeBase> parents=new HashMap<>(); Map<Long,AiDocument> docs=new HashMap<>();
        for (AiMessage message:messages) {
            message.setAttachedDocs(List.of());
            message.setCitationValidation(null);
            if (!safe(message)) continue;
            List<KnowledgeCitation> refs;
            try {refs=decode(message.getKnowledgeCitations());} catch (ServiceException e) {
                message.setCitationValidation(ChatKnowledgeCitationValidator.unverified());continue;
            }
            message.setCitationValidation(ChatKnowledgeCitationValidator.evaluate(message.getContent(),refs));
            Map<Long,RecognizedDoc> sources=new LinkedHashMap<>();
            for (var c:refs) {
                if (!parents.containsKey(c.knowledgeBaseId())) {
                    AiKnowledgeBase kb;
                    try {kb=knowledge.selectAccessibleKnowledgeBaseById(c.knowledgeBaseId());} catch (ServiceException e) {kb=null;}
                    parents.put(c.knowledgeBaseId(),kb);
                }
                if (!docs.containsKey(c.documentId())) docs.put(c.documentId(),documents.selectById(c.documentId()));
                var kb=parents.get(c.knowledgeBaseId()); var doc=docs.get(c.documentId());
                if (!accessible(kb,doc,c)) continue;
                var source=sources.computeIfAbsent(c.documentId(), ignored -> new RecognizedDoc(doc.getId(),doc.getName(),kb.getId(),"HISTORICAL",null,null));
                List<String> ids=new ArrayList<>(source.getCitationIds()); ids.add(c.citationId()); source.setCitationIds(ids);
            }
            message.setAttachedDocs(List.copyOf(sources.values()));
        }
    }

    public Preview preview(Long conversationId, Long messageId, String citationId) {
        AiMessage message=requireMessage(conversationId,messageId);
        if (citationId==null || !citationId.matches("S([1-9]|[1-4][0-9]|50)")) throw new ServiceException("引用编号无效",400);
        var ref=decode(message.getKnowledgeCitations()).stream().filter(c->c.citationId().equals(citationId)).findFirst()
                .orElseThrow(()->new ServiceException("该消息没有此引用",404));
        AiDocument doc=documents.selectById(ref.documentId());
        AiKnowledgeBase kb=knowledge.selectAccessibleKnowledgeBaseById(ref.knowledgeBaseId());
        requireCurrent(kb,doc,ref);
        if (ref.chunkNo()==null) throw unavailable(); // 旧向量缺少片段标识时不猜测定位。
        var row=snapshots.latest(kb,doc,true);
        requireSnapshot(row,kb,doc,ref);
        KnowledgeDocumentSnapshotStore.Payload payload;
        try {payload=snapshots.load(row);} catch (RuntimeException e) {throw unavailable();}
        var matches=payload.chunks().stream().filter(c->ref.chunkNo().equals(c.chunkNo())).toList();
        if (matches.size()!=1) throw unavailable();
        var chunk=matches.get(0);
        if (chunk.text()==null || !Objects.equals(ref.chunkHash(),chunk.contentHash())
                || !Objects.equals(ref.chunkHash(),KnowledgeDocumentSnapshotStore.digest(chunk.text()))
                || ref.includedChars()>chunk.text().length()
                || (!ref.truncated() && ref.includedChars()!=chunk.text().length())
                || (ref.truncated() && ref.includedChars()>=chunk.text().length())) throw unavailable();
        // 读取后再次确认会话/消息与来源。不是跨数据库/文件存储的原子授权事务。
        var currentMessage=requireMessage(conversationId,messageId);
        if (!Objects.equals(message.getKnowledgeCitations(),currentMessage.getKnowledgeCitations())
                || !Objects.equals(message.getContent(),currentMessage.getContent())) throw unavailable();
        var currentDoc=documents.selectById(ref.documentId());
        var currentKb=knowledge.selectAccessibleKnowledgeBaseById(ref.knowledgeBaseId());
        requireCurrent(currentKb,currentDoc,ref);
        var currentRow=snapshots.latest(currentKb,currentDoc,true); requireSnapshot(currentRow,currentKb,currentDoc,ref);
        if (!Objects.equals(row.getId(),currentRow.getId()) || !Objects.equals(row.getPayloadHash(),currentRow.getPayloadHash())) throw unavailable();
        if (!snapshots.sourceMatches(currentDoc,row)) throw unavailable();
        return new Preview(ref.citationId(),doc.getId(),currentDoc.getName(),ref.indexVersion(),ref.chunkNo(),
                chunk.locations()==null?List.of():List.copyOf(chunk.locations()),chunk.text().substring(0,ref.includedChars()),ref.truncated(),
                KnowledgeCitationOccurrences.inspect(currentMessage.getContent(), ref.citationId()));
    }

    private AiMessage requireMessage(Long conversationId, Long messageId) {
        var caller=CallerContextHolder.get();
        if (caller==null || caller.getUserId()==null || caller.getUserId()<=0) throw new ServiceException("请先登录",401);
        if (conversationId==null || conversationId<=0 || messageId==null || messageId<=0
                || chats.selectConversationById(conversationId,caller.getUserId())==null) throw new ServiceException("会话不存在或无权访问",404);
        var message=chats.selectMessageByIdAndConversation(messageId,conversationId);
        if (!safe(message) || !Objects.equals(message.getConversationId(),conversationId)) throw new ServiceException("引用消息不存在或不可用",404);
        return message;
    }
    private static boolean safe(AiMessage message) {return message!=null && "assistant".equals(message.getRole()) && "SAFE".equals(message.getModerationStatus());}
    private boolean accessible(AiKnowledgeBase kb, AiDocument doc, KnowledgeCitation ref) {
        var caller=CallerContextHolder.get();
        return caller!=null && caller.getUserId()!=null && caller.getUserId()>0 && kb!=null && doc!=null
                && Objects.equals(kb.getId(),ref.knowledgeBaseId()) && Objects.equals(doc.getId(),ref.documentId())
                && Objects.equals(doc.getKnowledgeBaseId(),kb.getId()) && "0".equals(doc.getDelFlag())
                && "2".equals(doc.getStatus()) && "SAFE".equals(doc.getModerationStatus())
                && (caller.isSuperAdmin() || Objects.equals(caller.getTenantId(),Objects.toString(kb.getTenantId(),null)));
    }
    private void requireCurrent(AiKnowledgeBase kb, AiDocument doc, KnowledgeCitation ref) {
        if (!accessible(kb,doc,ref)) throw new ServiceException("来源不存在或访问权限已失效",404);
        if (!"READY".equals(kb.getIndexStatus()) || !Objects.equals(kb.getIndexVersion(),ref.indexVersion())
                || !Objects.equals(KnowledgeDocumentSnapshotStore.digest(kb.getVectorCollection()),ref.collectionHash())) throw unavailable();
    }
    private void requireSnapshot(KnowledgeDocumentSnapshot row, AiKnowledgeBase kb, AiDocument doc, KnowledgeCitation ref) {
        if (row==null || !"READY".equals(row.getSnapshotState()) || !"NONE".equals(row.getPayloadCleanupState())
                || !Objects.equals(row.getTenantId(),kb.getTenantId()) || !Objects.equals(row.getKnowledgeBaseId(),kb.getId())
                || !Objects.equals(row.getDocumentId(),doc.getId()) || !Objects.equals(row.getIndexVersion(),ref.indexVersion())
                || !Objects.equals(row.getCollectionHash(),ref.collectionHash()) || !snapshots.sourceMatches(doc,row)) throw unavailable();
    }
    private static ServiceException unavailable() {return new ServiceException("原引用片段已失效或暂不可用；请重新提问，不以新版资料替代旧依据",409);}
}
