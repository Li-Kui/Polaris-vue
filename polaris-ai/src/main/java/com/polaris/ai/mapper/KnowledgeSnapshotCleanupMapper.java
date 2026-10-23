package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.knowledge.KnowledgeDocumentSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/** 只能从已授权父库派生租户。清理事务的锁序是父库 → 快照，不接受路径或 collection 参数。 */
public interface KnowledgeSnapshotCleanupMapper {
    String TENANT = KnowledgeIndexBuildMapper.TENANT;
    String ELIGIBLE = " WHERE knowledge_base_id = #{source.id} " + TENANT
            + " AND snapshot_state IN ('FAILED', 'RETIRED') AND payload_hash IS NOT NULL "
            + " AND payload_cleanup_attempts &lt; 5 AND ((payload_cleanup_state = 'NONE' "
            + " AND create_time &lt;= TIMESTAMPADD(HOUR, -24, CURRENT_TIMESTAMP)) "
            + " OR (payload_cleanup_state IN ('PENDING', 'RETRY_WAIT') AND payload_cleanup_after &lt;= CURRENT_TIMESTAMP)) "
            + "<if test='source.delFlag == \"0\" and source.vectorCollection != null'> AND collection_hash != #{activeHash}</if> ";

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_knowledge_base WHERE id = #{source.id} AND del_flag IN ('0', '2')", TENANT, "FOR UPDATE</script>"})
    AiKnowledgeBase lockParent(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT COUNT(*) FROM ai_knowledge_rebuild_task WHERE knowledge_base_id = #{source.id}", TENANT,
            "AND task_state IN ('QUEUED', 'RUNNING')</script>"})
    int activeTasks(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT COUNT(*) FROM ai_document d WHERE d.knowledge_base_id = #{source.id} AND d.index_owner_token IS NOT NULL",
            "AND EXISTS (SELECT 1 FROM ai_knowledge_base WHERE id = #{source.id}", TENANT, ")</script>"})
    int documentOwners(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_document_index_snapshot", ELIGIBLE, "ORDER BY id LIMIT 20 FOR UPDATE</script>"})
    List<KnowledgeDocumentSnapshot> eligible(@Param("source") AiKnowledgeBase source, @Param("activeHash") String activeHash);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_document_index_snapshot WHERE knowledge_base_id = #{source.id}", TENANT,
            "AND (snapshot_state IN ('FAILED', 'RETIRED') OR payload_cleanup_state != 'NONE')",
            "AND payload_hash IS NOT NULL <if test='beforeId != null'>AND id &lt; #{beforeId}</if>",
            "ORDER BY id DESC LIMIT 21</script>"})
    List<KnowledgeDocumentSnapshot> statusRows(@Param("source") AiKnowledgeBase source,@Param("beforeId") Long beforeId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT CURRENT_TIMESTAMP")
    Date databaseNow();

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_document_index_snapshot SET payload_cleanup_attempts = payload_cleanup_attempts + 1,",
            "payload_cleanup_state = #{state}, payload_cleanup_error = #{error},",
            "payload_cleanup_after = <choose><when test=\"state == 'RETRY_WAIT'\">TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP)</when><otherwise>NULL</otherwise></choose>",
            "WHERE id = #{row.id} AND knowledge_base_id = #{source.id}", TENANT,
            "AND snapshot_key = #{row.snapshotKey} AND snapshot_state = #{row.snapshotState}",
            "AND payload_cleanup_attempts = #{row.payloadCleanupAttempts} AND payload_cleanup_state = #{row.payloadCleanupState}</script>"})
    int finish(@Param("source") AiKnowledgeBase source, @Param("row") KnowledgeDocumentSnapshot row,
               @Param("state") String state, @Param("error") String error);
}
