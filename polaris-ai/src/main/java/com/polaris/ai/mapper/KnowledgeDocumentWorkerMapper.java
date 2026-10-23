package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.domain.AiDocument;
import com.polaris.ai.domain.AiKnowledgeBase;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 仅供 Fence 在父库锁事务内使用。文档无独立 tenant 列，必须由父库显式限定租户。 */
public interface KnowledgeDocumentWorkerMapper {
    String PARENT = " AND EXISTS (SELECT 1 FROM ai_knowledge_base WHERE id = #{source.id} "
            + KnowledgeIndexBuildMapper.TENANT + ") ";
    String DOCUMENT = " WHERE id = #{doc.id} AND knowledge_base_id = #{source.id} AND del_flag = '0' " + PARENT
            + "<choose><when test='doc.name != null'> AND name = #{doc.name}</when><otherwise> AND name IS NULL</otherwise></choose>"
            + "<choose><when test='doc.fileUrl != null'> AND file_url = #{doc.fileUrl}</when><otherwise> AND file_url IS NULL</otherwise></choose>";
    String ELIGIBLE = " AND COALESCE(moderation_status, '') NOT IN ('QUARANTINED', 'AUTO_DELETED') ";
    String OWNER = " AND index_owner_token = #{owner} AND index_parent_generation = #{source.indexGeneration} ";

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_document SET index_owner_token = #{owner}, index_parent_generation = #{source.indexGeneration},",
            "index_lease_until = TIMESTAMPADD(SECOND, 300, CURRENT_TIMESTAMP)", DOCUMENT, ELIGIBLE,
            "AND (index_owner_token IS NULL OR index_lease_until &lt;= CURRENT_TIMESTAMP",
            "OR index_parent_generation IS NULL OR index_parent_generation != #{source.indexGeneration})</script>"})
    int claim(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc, @Param("owner") String owner);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT id, knowledge_base_id, name, file_url FROM ai_document", DOCUMENT, OWNER, ELIGIBLE,
            "AND index_lease_until &gt; CURRENT_TIMESTAMP FOR UPDATE</script>"})
    AiDocument lockOwned(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc, @Param("owner") String owner);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT id, knowledge_base_id, name, file_url FROM ai_document", DOCUMENT, OWNER, ELIGIBLE,
            "AND status IN ('0', '1') FOR UPDATE</script>"})
    AiDocument lockForFailure(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc, @Param("owner") String owner);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_document SET index_lease_until = TIMESTAMPADD(SECOND, 300, CURRENT_TIMESTAMP)", DOCUMENT, OWNER, ELIGIBLE,
            "AND index_lease_until &gt; CURRENT_TIMESTAMP</script>"})
    int heartbeat(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc, @Param("owner") String owner);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_document SET status = '3', remark = '文档处理未完成，请检查诊断后显式重建'", DOCUMENT, OWNER, ELIGIBLE,
            "AND status IN ('0', '1')</script>"})
    int failOwned(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc, @Param("owner") String owner);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_document SET status = '3', remark = '文档处理未开始，请检查配置后显式重建'", DOCUMENT, ELIGIBLE,
            "AND status = '0' AND index_owner_token IS NULL</script>"})
    int failPending(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT id, knowledge_base_id, name, file_url FROM ai_document", DOCUMENT, ELIGIBLE,
            "AND status = '0' AND index_owner_token IS NULL FOR UPDATE</script>"})
    AiDocument lockPending(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_document SET index_owner_token = NULL, index_lease_until = NULL",
            "WHERE id = #{doc.id} AND knowledge_base_id = #{source.id}", PARENT, OWNER, "</script>"})
    int release(@Param("source") AiKnowledgeBase source, @Param("doc") AiDocument doc, @Param("owner") String owner);
}
