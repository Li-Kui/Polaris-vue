package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.knowledge.KnowledgeRebuildTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 所有任务读写从已授权的父库派生租户，不能信任请求提供的 tenantId。 */
public interface KnowledgeRebuildTaskMapper {
    String TENANT = KnowledgeIndexBuildMapper.TENANT;
    String FIELDS = "task_id, tenant_id, knowledge_base_id, request_key, source_generation, claimed_generation, "
            + "task_state, owner_token, lease_until, total_documents, completed_documents, error_code, create_by, "
            + "create_time, start_time, finish_time, task_type, document_id, CASE WHEN lease_until &lt;= CURRENT_TIMESTAMP THEN 1 ELSE 0 END AS expired";
    String TASK = " WHERE knowledge_base_id = #{source.id} AND task_id = #{id} " + TENANT;
    String LIVE = " AND task_state = 'RUNNING' AND owner_token = #{owner} AND lease_until &gt; CURRENT_TIMESTAMP ";

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_knowledge_base WHERE id = #{source.id} AND del_flag = '0'", TENANT, "FOR UPDATE</script>"})
    AiKnowledgeBase lockParent(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT", FIELDS, "FROM ai_knowledge_rebuild_task", TASK, "</script>"})
    KnowledgeRebuildTask find(@Param("source") AiKnowledgeBase source, @Param("id") String id);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT", FIELDS, "FROM ai_knowledge_rebuild_task", TASK, "FOR UPDATE</script>"})
    KnowledgeRebuildTask lock(@Param("source") AiKnowledgeBase source, @Param("id") String id);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT", FIELDS, "FROM ai_knowledge_rebuild_task WHERE knowledge_base_id = #{source.id}", TENANT,
            "AND request_key = #{key} LIMIT 1</script>"})
    KnowledgeRebuildTask byRequest(@Param("source") AiKnowledgeBase source, @Param("key") String key);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT", FIELDS, "FROM ai_knowledge_rebuild_task WHERE knowledge_base_id = #{source.id}", TENANT,
            "AND active_knowledge_id = #{source.id} LIMIT 1</script>"})
    KnowledgeRebuildTask active(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT", FIELDS, "FROM ai_knowledge_rebuild_task WHERE knowledge_base_id = #{source.id}", TENANT,
            "ORDER BY create_time DESC, task_id DESC LIMIT 1</script>"})
    KnowledgeRebuildTask latest(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO ai_knowledge_rebuild_task (task_id, tenant_id, knowledge_base_id, active_knowledge_id, request_key, "
            + "source_generation, task_state, lease_until, create_by, task_type, document_id) VALUES (#{row.taskId}, #{row.tenantId}, #{row.knowledgeBaseId}, "
            + "#{row.knowledgeBaseId}, #{row.requestKey}, #{row.sourceGeneration}, 'QUEUED', TIMESTAMPADD(SECOND, 300, CURRENT_TIMESTAMP), #{row.createBy}, #{row.taskType}, #{row.documentId})")
    int insert(@Param("row") KnowledgeRebuildTask row);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET task_state = 'RUNNING', owner_token = #{owner},",
            "claimed_generation = #{generation}, start_time = CURRENT_TIMESTAMP, lease_until = TIMESTAMPADD(SECOND, 300, CURRENT_TIMESTAMP)",
            TASK, "AND task_state = 'QUEUED' AND lease_until &gt; CURRENT_TIMESTAMP</script>"})
    int claim(@Param("source") AiKnowledgeBase source, @Param("id") String id, @Param("owner") String owner, @Param("generation") long generation);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET lease_until = TIMESTAMPADD(SECOND, 300, CURRENT_TIMESTAMP)", TASK, LIVE, "</script>"})
    int heartbeat(@Param("source") AiKnowledgeBase source, @Param("id") String id, @Param("owner") String owner);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET document_ids_json = #{ids}, total_documents = #{total}, completed_documents = 0", TASK, LIVE, "</script>"})
    int manifest(@Param("source") AiKnowledgeBase source, @Param("id") String id, @Param("owner") String owner,
                 @Param("ids") String ids, @Param("total") int total);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET completed_documents = completed_documents + 1", TASK, LIVE,
            "AND completed_documents &lt; total_documents</script>"})
    int progress(@Param("source") AiKnowledgeBase source, @Param("id") String id, @Param("owner") String owner);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET task_state = #{state}, error_code = #{error},",
            "active_knowledge_id = NULL, finish_time = CURRENT_TIMESTAMP", TASK,
            "AND task_state = 'RUNNING' AND owner_token = #{owner}",
            "<if test=\"state == 'SUCCEEDED'\">AND lease_until &gt; CURRENT_TIMESTAMP",
            "AND document_ids_json IS NOT NULL AND completed_documents = total_documents</if></script>"})
    int finish(@Param("source") AiKnowledgeBase source, @Param("id") String id, @Param("owner") String owner,
               @Param("state") String state, @Param("error") String error);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET task_state = 'REJECTED', error_code = 'QUEUE_REJECTED',",
            "active_knowledge_id = NULL, finish_time = CURRENT_TIMESTAMP", TASK, "AND task_state = 'QUEUED'</script>"})
    int reject(@Param("source") AiKnowledgeBase source, @Param("id") String id);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET task_state = 'INVALIDATED', error_code = 'SOURCE_CHANGED',",
            "active_knowledge_id = NULL, finish_time = CURRENT_TIMESTAMP", TASK, "AND task_state = 'QUEUED'</script>"})
    int invalidateQueued(@Param("source") AiKnowledgeBase source, @Param("id") String id);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_rebuild_task SET task_state = 'INTERRUPTED', error_code = 'LEASE_EXPIRED',",
            "active_knowledge_id = NULL, finish_time = CURRENT_TIMESTAMP", TASK,
            "AND task_state IN ('QUEUED', 'RUNNING') AND lease_until &lt;= CURRENT_TIMESTAMP</script>"})
    int expire(@Param("source") AiKnowledgeBase source, @Param("id") String id);
}
