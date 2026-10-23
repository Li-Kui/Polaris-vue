package com.polaris.ai.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.polaris.ai.agent.collaboration.AgentBindingRow;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AgentBindingMapper {
    @Select("SELECT * FROM ai_agent_mcp_binding WHERE tenant_id=#{tenantId} AND agent_id=#{agentId} ORDER BY alias")
    List<AgentBindingRow> list(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    @Delete("DELETE FROM ai_agent_mcp_binding WHERE tenant_id=#{tenantId} AND agent_id=#{agentId}")
    int delete(@Param("tenantId") Long tenantId, @Param("agentId") Long agentId);
    @Insert("INSERT INTO ai_agent_mcp_binding (tenant_id,agent_id,alias,tool_id,purpose,query_argument,fixed_arguments_json,input_schema_json,tool_revision,connection_revision,catalog_revision,structure_hash) "
            + "VALUES(#{tenantId},#{agentId},#{alias},#{toolId},#{purpose},#{queryArgument},#{fixedArgumentsJson},#{inputSchemaJson},#{toolRevision},#{connectionRevision},#{catalogRevision},#{structureHash})")
    int insert(AgentBindingRow row);
}
