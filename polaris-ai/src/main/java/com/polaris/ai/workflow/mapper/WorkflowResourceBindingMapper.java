package com.polaris.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.polaris.ai.workflow.domain.WorkflowResourceBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 按所有者和环境隔离的工作流资源绑定数据访问接口。 */
@Mapper
public interface WorkflowResourceBindingMapper extends BaseMapper<WorkflowResourceBinding> {

    @Select("SELECT * FROM ai_workflow_resource_binding "
            + "WHERE owner_type = #{ownerType} AND owner_id = #{ownerId} "
            + "AND environment = #{environment} AND resource_kind = #{kind} "
            + "AND resource_key = #{key} AND status = 'ACTIVE' LIMIT 1")
    WorkflowResourceBinding selectActive(
            @Param("ownerType") String ownerType,
            @Param("ownerId") Long ownerId,
            @Param("environment") String environment,
            @Param("kind") String kind,
            @Param("key") String key);
}
