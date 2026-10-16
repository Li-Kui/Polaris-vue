package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.domain.AiKnowledgeBase;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 后台发布操作使用父库派生身份；即使没有请求线程也必须显式限定租户。 */
public interface KnowledgeIndexBuildMapper {
    String TENANT = "<choose><when test='source.tenantId != null'> AND tenant_id = #{source.tenantId}</when>"
            + "<otherwise> AND tenant_id IS NULL</otherwise></choose>";
    String POINTER = "<choose><when test='source.indexVersion != null'> AND index_version = #{source.indexVersion}</when>"
            + "<otherwise> AND index_version IS NULL</otherwise></choose>"
            + "<choose><when test='source.vectorCollection != null'> AND vector_collection = #{source.vectorCollection}</when>"
            + "<otherwise> AND vector_collection IS NULL</otherwise></choose>";

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>", "UPDATE ai_knowledge_base SET index_generation = index_generation + 1,",
            "index_status = 'BUILDING', index_error = NULL WHERE id = #{source.id} AND del_flag = '0'",
            "AND index_generation = #{generation}", TENANT, POINTER, "</script>"})
    int begin(@Param("source") AiKnowledgeBase source, @Param("generation") long generation);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>", "UPDATE ai_knowledge_base SET index_generation = index_generation + 1,",
            "index_error = CASE WHEN index_status = 'BUILDING' THEN '资料已变化，请重新重建索引' ELSE index_error END,",
            "index_status = CASE WHEN index_status = 'BUILDING' THEN 'STALE' ELSE index_status END",
            "WHERE id = #{source.id} AND del_flag = '0'", TENANT, "</script>"})
    int documentChanged(@Param("source") AiKnowledgeBase source);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>", "UPDATE ai_knowledge_base SET index_status = 'FAILED',",
            "index_error = '索引构建未完成，请检查文档诊断后显式重建'",
            "WHERE id = #{source.id} AND del_flag = '0' AND index_generation = #{generation} AND index_status = 'BUILDING'",
            TENANT, POINTER, "</script>"})
    int fail(@Param("source") AiKnowledgeBase source, @Param("generation") long generation);

    @InterceptorIgnore(tenantLine = "true")
    @Update({"<script>", "<bind name='documentCount' value='documentIds.size()'/>", "UPDATE ai_knowledge_base SET",
            "embedding_dimension = #{candidate.embeddingDimension}, embedding_model_revision = #{candidate.embeddingModelRevision},",
            "embedding_schema_hash = #{candidate.embeddingSchemaHash}, index_version = #{candidate.indexVersion},",
            "vector_collection = #{candidate.vectorCollection}, index_signature = #{candidate.indexSignature},",
            "index_status = #{candidate.indexStatus}, index_error = NULL",
            "WHERE id = #{source.id} AND del_flag = '0' AND index_generation = #{generation} AND index_status = 'BUILDING'",
            TENANT, POINTER,
            "<choose><when test='source.embeddingModelId != null'> AND embedding_model_id = #{source.embeddingModelId}</when><otherwise> AND embedding_model_id IS NULL</otherwise></choose>",
            "<choose><when test='source.embeddingDimension != null'> AND embedding_dimension = #{source.embeddingDimension}</when><otherwise> AND embedding_dimension IS NULL</otherwise></choose>",
            "<choose><when test='source.embeddingModelRevision != null'> AND embedding_model_revision = #{source.embeddingModelRevision}</when><otherwise> AND embedding_model_revision IS NULL</otherwise></choose>",
            "<choose><when test='source.embeddingSchemaHash != null'> AND embedding_schema_hash = #{source.embeddingSchemaHash}</when><otherwise> AND embedding_schema_hash IS NULL</otherwise></choose>",
            "<choose><when test='source.chunkSize != null'> AND chunk_size = #{source.chunkSize}</when><otherwise> AND chunk_size IS NULL</otherwise></choose>",
            "<choose><when test='source.chunkOverlap != null'> AND chunk_overlap = #{source.chunkOverlap}</when><otherwise> AND chunk_overlap IS NULL</otherwise></choose>",
            "<choose><when test='source.splitterType != null'> AND splitter_type = #{source.splitterType}</when><otherwise> AND splitter_type IS NULL</otherwise></choose>",
            "<choose><when test='source.deptId != null'> AND dept_id = #{source.deptId}</when><otherwise> AND dept_id IS NULL</otherwise></choose>",
            // 数量 + 唯一 ID 集合 + 状态在切换语句中再次验证，不能只依赖之前的 Java 查询。
            "AND (SELECT COUNT(*) FROM ai_document d WHERE d.knowledge_base_id = #{source.id} AND d.del_flag = '0') = #{documentCount}",
            "AND NOT EXISTS (SELECT 1 FROM ai_document d WHERE d.knowledge_base_id = #{source.id} AND d.del_flag = '0' AND (",
            "<choose><when test='documentIds.size > 0'>d.id NOT IN",
            "<foreach collection='documentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</when><otherwise>1 = 1</otherwise></choose>",
            "OR NOT (COALESCE(d.status, '') = '2' AND COALESCE(d.moderation_status, '') = 'SAFE'",
            "<if test='excludedIds.size > 0'> AND d.id NOT IN",
            "<foreach collection='excludedIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            " OR (d.id IN",
            "<foreach collection='excludedIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "AND COALESCE(d.moderation_status, '') IN ('QUARANTINED', 'AUTO_DELETED'))</if>",
            ")))", "</script>"})
    int activate(@Param("source") AiKnowledgeBase source, @Param("generation") long generation,
                 @Param("candidate") AiKnowledgeBase candidate, @Param("documentIds") List<Long> documentIds,
                 @Param("excludedIds") List<Long> excludedIds);
}
