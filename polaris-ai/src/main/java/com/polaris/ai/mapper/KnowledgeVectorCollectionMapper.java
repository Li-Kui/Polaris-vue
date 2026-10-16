package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.ai.knowledge.KnowledgeVectorCollection;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/** 后台操作仅使用父库派生身份，所有内部查询显式限定租户。 */
public interface KnowledgeVectorCollectionMapper {
    String TENANT = KnowledgeIndexBuildMapper.TENANT;
    String OWNER = " AND registry_id = #{handle.registryId} AND build_generation = #{handle.generation}"
            + " AND backend_key = #{handle.backendKey} AND collection_name = #{handle.collectionName}"
            + " AND ownership_version = 1 AND ownership_marker = #{handle.ownershipMarker}";

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_knowledge_base WHERE id = #{source.id} AND del_flag IN ('0','2')",TENANT,"FOR UPDATE</script>"})
    AiKnowledgeBase lockParent(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO ai_knowledge_vector_collection (registry_id,tenant_id,knowledge_base_id,build_generation,index_version,backend_type,backend_key,collection_name,registry_state,ownership_version,ownership_marker)"
            + " VALUES (#{registryId},#{tenantId},#{knowledgeBaseId},#{buildGeneration},#{indexVersion},#{backendType},#{backendKey},#{collectionName},'RESERVED',#{ownershipVersion},#{ownershipMarker})")
    int insert(KnowledgeVectorCollection row);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_vector_collection SET registry_state = #{next},update_time = CURRENT_TIMESTAMP(3),",
            "creation_acknowledged = CASE WHEN #{next} = 'WRITING' THEN 1 ELSE creation_acknowledged END",
            "WHERE knowledge_base_id = #{source.id}",TENANT,OWNER,"AND registry_state = #{expected}</script>"})
    int advance(@Param("source") AiKnowledgeBase source,@Param("handle") com.polaris.ai.knowledge.KnowledgeVectorCollectionRegistry.Handle handle,
                @Param("expected") String expected,@Param("next") String next);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_knowledge_vector_collection WHERE knowledge_base_id = #{source.id}",TENANT,
            "AND backend_key = #{backendKey} AND collection_name = #{name} FOR UPDATE</script>"})
    KnowledgeVectorCollection candidate(@Param("source") AiKnowledgeBase source,@Param("backendKey") String backendKey,@Param("name") String name);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_knowledge_vector_collection WHERE knowledge_base_id = #{source.id}",TENANT,
            "AND build_generation = #{generation}</script>"})
    KnowledgeVectorCollection find(@Param("source") AiKnowledgeBase source,@Param("generation") long generation);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_vector_collection SET registry_state = 'RETIRED',retire_time = CURRENT_TIMESTAMP(3),update_time = CURRENT_TIMESTAMP(3)",
            "WHERE knowledge_base_id = #{source.id}",TENANT,"AND backend_key = #{backendKey} AND collection_name = #{source.vectorCollection} AND registry_state = 'ACTIVE'</script>"})
    int retirePrevious(@Param("source") AiKnowledgeBase source,@Param("backendKey") String backendKey);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>UPDATE ai_knowledge_vector_collection SET registry_state = CASE WHEN registry_state IN ('CREATING','WRITING') THEN 'UNKNOWN' ELSE 'ABANDONED' END,",
            "error_code = 'BUILD_NOT_ACTIVATED',update_time = CURRENT_TIMESTAMP(3)",
            "WHERE knowledge_base_id = #{source.id}",TENANT,OWNER,"AND registry_state IN ('RESERVED','CREATING','WRITING','BUILT')</script>"})
    int abandon(@Param("source") AiKnowledgeBase source,@Param("handle") com.polaris.ai.knowledge.KnowledgeVectorCollectionRegistry.Handle handle);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_knowledge_vector_collection WHERE knowledge_base_id = #{source.id}",TENANT,
            "ORDER BY create_time DESC,registry_id DESC LIMIT 20</script>"})
    List<KnowledgeVectorCollection> history(@Param("source") AiKnowledgeBase source);

    /** 只预览最早的 20 条登记，多取一条说明截断；不扫描远端或未登记集合。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT * FROM ai_knowledge_vector_collection WHERE knowledge_base_id = #{source.id}",TENANT,
            "ORDER BY create_time,registry_id LIMIT 21</script>"})
    List<KnowledgeVectorCollection> cleanupPreview(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT CURRENT_TIMESTAMP(3)")
    Date databaseNow();

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT CASE WHEN EXISTS (SELECT 1 FROM ai_knowledge_rebuild_task WHERE knowledge_base_id = #{source.id}",TENANT,
            "AND task_state IN ('QUEUED','RUNNING')) OR EXISTS (SELECT 1 FROM ai_document d WHERE d.knowledge_base_id = #{source.id}",
            "AND d.index_owner_token IS NOT NULL AND EXISTS (SELECT 1 FROM ai_knowledge_base WHERE id = #{source.id}",TENANT,")) THEN 1 ELSE 0 END</script>"})
    boolean hasActiveWork(@Param("source") AiKnowledgeBase source);

    /** 跨父库/租户的保守引用保护，包含软删除和空租户；仅返回是否存在，不输出其他资源身份。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT EXISTS (SELECT 1 FROM ai_knowledge_base WHERE vector_collection = #{name})")
    boolean hasAnyReference(@Param("name") String name);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>SELECT EXISTS (SELECT 1 FROM ai_document_index_snapshot WHERE knowledge_base_id = #{source.id}",TENANT,
            "AND collection_hash = #{hash} AND snapshot_state = 'PREPARED')</script>"})
    boolean hasPreparedSnapshot(@Param("source") AiKnowledgeBase source,@Param("hash") String hash);
}
