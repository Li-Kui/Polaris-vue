package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.domain.AiAgent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI智能体定义数据访问层
 *
 * @author polaris
 */
public interface AiAgentMapper extends BaseMapper<AiAgent> {

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT * FROM ai_agent WHERE id = #{id} AND del_flag = '0'",
            "<choose><when test='tenantId != null'>AND tenant_id = #{tenantId}</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "LIMIT 1", "</script>"})
    AiAgent selectWorkflowResource(
            @Param("tenantId") Long tenantId, @Param("id") Long id);

    @InterceptorIgnore(tenantLine = "true")
    @Select({"<script>",
            "SELECT * FROM ai_agent WHERE del_flag = '0'",
            "<choose><when test='tenantId != null'>AND tenant_id = #{tenantId}</when>",
            "<otherwise>AND tenant_id IS NULL</otherwise></choose>",
            "ORDER BY agent_name, id",
            "</script>"})
    List<AiAgent> selectWorkflowResources(@Param("tenantId") Long tenantId);
}
