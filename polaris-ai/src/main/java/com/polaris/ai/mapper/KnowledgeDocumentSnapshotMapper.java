package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.knowledge.KnowledgeDocumentSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface KnowledgeDocumentSnapshotMapper extends BaseMapper<KnowledgeDocumentSnapshot> {

    /** 客服发布只读预检：当前索引至少有一个未清理、文档仍有效的可引用快照。 */
    @Select("""
            select exists (
                select 1 from ai_document_index_snapshot s
                join ai_document d on d.id = s.document_id and d.knowledge_base_id = s.knowledge_base_id
                where s.tenant_id = #{tenantId} and s.knowledge_base_id = #{knowledgeBaseId}
                    and s.index_version = #{indexVersion} and s.collection_hash = #{collectionHash}
                    and s.snapshot_state = 'READY' and s.payload_cleanup_state = 'NONE' and s.chunk_count > 0
                    and d.del_flag = '0' and d.status = '2' and d.moderation_status = 'SAFE'
            )
            """)
    boolean hasReadyCustomerSource(@Param("tenantId") Long tenantId, @Param("knowledgeBaseId") Long knowledgeBaseId,
                                   @Param("indexVersion") Long indexVersion, @Param("collectionHash") String collectionHash);
}
