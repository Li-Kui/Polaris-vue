package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.knowledge.KnowledgeVectorCollection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 父库→登记行锁；独立短事务先记录 owner/派发意图，不持数据库锁等待远端。 */
public interface KnowledgeVectorCleanupMapper {
    String TENANT=KnowledgeIndexBuildMapper.TENANT;
    String ROW=" WHERE knowledge_base_id = #{source.id} "+TENANT+" AND registry_id = #{row.registryId}"
            +" AND build_generation = #{row.buildGeneration} AND index_version = #{row.indexVersion}"
            +" AND backend_type = #{row.backendType} AND backend_key = #{row.backendKey} AND collection_name = #{row.collectionName}"
            +" AND ownership_version = 1 AND ownership_marker = #{row.ownershipMarker} AND creation_acknowledged = 1"
            +" AND registry_state = #{row.registryState}";
    String OWNER=" AND cleanup_state = 'RUNNING' AND cleanup_owner_token = #{owner} AND cleanup_plan_key = #{row.cleanupPlanKey}";

    @InterceptorIgnore(tenantLine="true")
    @Select({"<script>SELECT * FROM ai_knowledge_vector_collection WHERE knowledge_base_id = #{source.id}",TENANT,
            "AND build_generation = #{generation} FOR UPDATE</script>"})
    KnowledgeVectorCollection lock(@Param("source") AiKnowledgeBase source,@Param("generation") long generation);

    @InterceptorIgnore(tenantLine="true")
    @Update({"<script>UPDATE ai_knowledge_vector_collection SET cleanup_state='RUNNING',cleanup_attempts=cleanup_attempts+1,",
            "cleanup_requested_by=#{userId},cleanup_requested_at=CURRENT_TIMESTAMP(3),",
            "cleanup_owner_token=#{owner},cleanup_plan_key=#{plan},cleanup_lease_until=TIMESTAMPADD(MINUTE,2,CURRENT_TIMESTAMP(3)),",
            "cleanup_after=NULL,cleanup_error=NULL,cleanup_dispatched=0",ROW,
            "AND cleanup_attempts=#{row.cleanupAttempts} AND cleanup_attempts &lt; 5 AND (cleanup_state='NONE' OR",
            "(cleanup_state='RETRY_WAIT' AND cleanup_after &lt;= CURRENT_TIMESTAMP(3)))</script>"})
    int claim(@Param("source") AiKnowledgeBase source,@Param("row") KnowledgeVectorCollection row,@Param("owner") String owner,@Param("plan") String plan,@Param("userId") Long userId);

    @InterceptorIgnore(tenantLine="true")
    @Update({"<script>UPDATE ai_knowledge_vector_collection SET cleanup_dispatched=1",ROW,OWNER,
            "AND cleanup_dispatched=0 AND cleanup_lease_until &gt; CURRENT_TIMESTAMP(3)</script>"})
    int dispatch(@Param("source") AiKnowledgeBase source,@Param("row") KnowledgeVectorCollection row,@Param("owner") String owner);

    @InterceptorIgnore(tenantLine="true")
    @Update({"<script>UPDATE ai_knowledge_vector_collection SET cleanup_state=#{state},cleanup_error=#{error},cleanup_owner_token=NULL,cleanup_lease_until=NULL,",
            "cleanup_after=<choose><when test=\"state == 'RETRY_WAIT'\">TIMESTAMPADD(MINUTE,5,CURRENT_TIMESTAMP(3))</when><otherwise>NULL</otherwise></choose>,",
            "cleanup_confirmed_at=<choose><when test=\"state == 'SUCCEEDED'\">CURRENT_TIMESTAMP(3)</when><otherwise>NULL</otherwise></choose>",ROW,OWNER,
            "AND cleanup_lease_until &gt; CURRENT_TIMESTAMP(3)</script>"})
    int finish(@Param("source") AiKnowledgeBase source,@Param("row") KnowledgeVectorCollection row,@Param("owner") String owner,@Param("state") String state,@Param("error") String error);

    @InterceptorIgnore(tenantLine="true")
    @Update({"<script>UPDATE ai_knowledge_vector_collection SET cleanup_state='MANUAL_REQUIRED',cleanup_error='INTERRUPTED_RESULT_UNKNOWN',",
            "cleanup_owner_token=NULL,cleanup_lease_until=NULL,cleanup_after=NULL",ROW,
            "AND cleanup_state='RUNNING' AND cleanup_lease_until &lt;= CURRENT_TIMESTAMP(3)</script>"})
    int stopExpired(@Param("source") AiKnowledgeBase source,@Param("row") KnowledgeVectorCollection row);

    /** 仅在远端删除 ACK + 不存在回读后，同事务精确退役此集合的快照；不删除文件或其他集合快照。 */
    @InterceptorIgnore(tenantLine="true")
    @Update({"<script>UPDATE ai_document_index_snapshot SET snapshot_state='RETIRED',",
            "payload_cleanup_after=CASE WHEN payload_hash IS NOT NULL AND payload_cleanup_state='NONE' THEN TIMESTAMPADD(HOUR,24,CURRENT_TIMESTAMP(3)) ELSE payload_cleanup_after END,",
            "payload_cleanup_state=CASE WHEN payload_hash IS NOT NULL AND payload_cleanup_state='NONE' THEN 'PENDING' ELSE payload_cleanup_state END",
            "WHERE knowledge_base_id = #{source.id}",TENANT,"AND collection_hash=#{hash} AND snapshot_state IN ('READY','FAILED','RETIRED')</script>"})
    int retireSnapshots(@Param("source") AiKnowledgeBase source,@Param("hash") String hash);
}
