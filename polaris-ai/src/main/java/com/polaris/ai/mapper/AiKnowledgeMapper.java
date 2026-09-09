package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiKnowledgeBase;
import com.polaris.common.annotation.DataScope;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 知识库管理数据访问层
 * 
 * @author polaris
 */
public interface AiKnowledgeMapper extends BaseMapper<AiKnowledgeBase>
{
    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT * FROM ai_knowledge_base WHERE id = #{id} AND del_flag = '0'",
            "<choose><when test='tenantId != null'>AND tenant_id = #{tenantId}</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "LIMIT 1", "</script>"})
    AiKnowledgeBase selectWorkflowResource(
            @Param("tenantId") Long tenantId, @Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT * FROM ai_knowledge_base WHERE del_flag = '0'",
            "<choose><when test='tenantId != null'>AND tenant_id = #{tenantId}</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "ORDER BY name, id",
            "</script>"})
    List<AiKnowledgeBase> selectWorkflowResources(@Param("tenantId") Long tenantId);

    // ================================================================
    //  知识库主表操作
    // ================================================================

    /**
     * 新增知识库
     */
    int insertKnowledgeBase(AiKnowledgeBase knowledgeBase);

    /**
     * 修改知识库信息
     */
    int updateKnowledgeBase(AiKnowledgeBase knowledgeBase);

    /**
     * 查询知识库列表
     */
    @DataScope(deptAlias = "kb")
    List<AiKnowledgeBase> selectKnowledgeBaseList(AiKnowledgeBase knowledgeBase);

    /**
     * 根据 ID 获取知识库详情
     */
    AiKnowledgeBase selectKnowledgeBaseById(Long id);

    /**
     * 根据当前登录用户的数据范围查询知识库。
     */
    @DataScope(deptAlias = "kb")
    AiKnowledgeBase selectAccessibleKnowledgeBaseById(AiKnowledgeBase knowledgeBase);
}
