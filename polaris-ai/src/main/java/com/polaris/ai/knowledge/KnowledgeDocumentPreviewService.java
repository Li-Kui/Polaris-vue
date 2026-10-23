package com.polaris.ai.knowledge;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.mapper.AiDocumentMapper;
import com.polaris.ai.service.IAiKnowledgeService;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class KnowledgeDocumentPreviewService {
    private final IAiKnowledgeService knowledge;
    private final AiDocumentMapper documents;
    private final KnowledgeDocumentSnapshotStore snapshots;
    public KnowledgeDocumentPreviewService(IAiKnowledgeService knowledge, AiDocumentMapper documents, KnowledgeDocumentSnapshotStore snapshots) {
        this.knowledge = knowledge; this.documents = documents; this.snapshots = snapshots;
    }

    public Preview preview(Long documentId, int page, int size) {
        var caller = CallerContextHolder.get();
        if (caller == null || caller.getUserId() == null || caller.getUserId() <= 0
                || !(caller.isSuperAdmin() || (caller.isPlatformMode() && caller.isTenantAdmin())))
            throw new ServiceException("仅系统超级管理员或当前租户管理员可查看文档诊断", 403);
        if (documentId == null || documentId <= 0 || page < 1 || page > 2000 || size < 1 || size > 20)
            throw new ServiceException("分页参数无效", 400);
        AiDocument doc = documents.selectById(documentId);
        AiKnowledgeBase kb = requireAccessible(doc);
        boolean active = "READY".equals(kb.getIndexStatus());
        KnowledgeDocumentSnapshot row = snapshots.latest(kb, doc, active);
        if (row == null) return empty(doc, kb, active && "2".equals(doc.getStatus()) && "SAFE".equals(doc.getModerationStatus())
                ? "LEGACY_NO_SNAPSHOT" : "UNAVAILABLE", null, page, size);
        if (!Objects.equals(row.getKnowledgeBaseId(), kb.getId()) || !Objects.equals(row.getDocumentId(), doc.getId())
                || !Objects.equals(row.getTenantId(), kb.getTenantId())) throw new ServiceException("文档快照不可用", 409);
        if (!active || !"2".equals(doc.getStatus()) || !"SAFE".equals(doc.getModerationStatus()) || !"READY".equals(row.getSnapshotState()))
            return empty(doc, kb, "UNAVAILABLE", row.getErrorCode(), page, size);
        if (!Objects.equals(row.getIndexVersion(), kb.getIndexVersion())
                || !Objects.equals(row.getCollectionHash(), KnowledgeDocumentSnapshotStore.digest(kb.getVectorCollection()))
                || !snapshots.sourceMatches(doc, row)) throw new ServiceException("文档或索引已变化，快照不可用", 409);
        KnowledgeDocumentSnapshotStore.Payload payload;
        try {payload = snapshots.load(row);} catch (RuntimeException e) {throw new ServiceException("分块快照暂不可用，请检查私有存储", 409);}
        int start = Math.min((page - 1) * size, payload.chunks().size());
        List<ChunkView> chunks = payload.chunks().subList(start, Math.min(start + size, payload.chunks().size())).stream()
                .map(chunk -> new ChunkView(chunk.chunkNo(), chunk.text(), chunk.contentHash(), chunk.locations())).toList();
        // 读取载荷后再次核验入口授权/来源状态及活动索引，不把检查宣称成跨存储原子事务。
        AiDocument currentDoc = documents.selectById(documentId);
        AiKnowledgeBase current = requireAccessible(currentDoc);
        if (!"2".equals(currentDoc.getStatus()) || !"SAFE".equals(currentDoc.getModerationStatus())
                || !"READY".equals(current.getIndexStatus()) || !Objects.equals(kb.getIndexVersion(), current.getIndexVersion())
                || !Objects.equals(kb.getVectorCollection(), current.getVectorCollection()) || !snapshots.sourceMatches(currentDoc, row))
            throw new ServiceException("文档或索引已变化，快照不可用", 409);
        return new Preview("READY", doc.getId(), doc.getName(), kb.getIndexVersion(), row.getParserVersion(), row.getSplitterVersion(),
                row.getParsedChars(), row.getChunkCount(), null, page, size, row.getWarningsJson(), chunks);
    }

    private AiKnowledgeBase requireAccessible(AiDocument doc) {
        if (doc == null || !"0".equals(doc.getDelFlag())) throw new ServiceException("文档不存在或无权访问", 404);
        AiKnowledgeBase kb = knowledge.selectAccessibleKnowledgeBaseById(doc.getKnowledgeBaseId());
        var caller = CallerContextHolder.require();
        if (kb == null || (!caller.isSuperAdmin() && !Objects.equals(caller.getTenantId(), Objects.toString(kb.getTenantId(), null))))
            throw new ServiceException("文档不存在或无权访问", 404);
        return kb;
    }
    private Preview empty(AiDocument doc, AiKnowledgeBase kb, String state, String error, int page, int size) {
        return new Preview(state, doc.getId(), doc.getName(), kb.getIndexVersion(), null, null, null, 0, error, page, size, "[]", List.of());
    }
    public record ChunkView(String chunkNo, String text, String contentHash, List<String> locations) { }
    public record Preview(String state, Long documentId, String documentName, Long indexVersion, String parserVersion,
                          String splitterVersion, Integer parsedChars, int chunkCount, String errorCode,
                          int page, int pageSize, String warningsJson, List<ChunkView> chunks) { }
}
