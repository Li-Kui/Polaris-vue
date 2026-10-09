package com.polaris.platform.mapper;

import com.polaris.platform.domain.WorkflowShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流分享 Mapper 接口
 */
@Mapper
public interface WorkflowShareMapper {

    /**
     * 根据主键查询
     */
    WorkflowShare selectById(@Param("id") Long id);

    /**
     * 根据分享短码查询
     */
    WorkflowShare selectByShareCode(@Param("shareCode") String shareCode);

    /**
     * 根据租户查询分享列表
     */
    List<WorkflowShare> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据工作流定义查询分享列表
     */
    List<WorkflowShare> selectByWorkflowDefinitionId(@Param("tenantId") Long tenantId, @Param("workflowDefinitionId") Long workflowDefinitionId);

    /**
     * 新增工作流分享
     */
    int insert(WorkflowShare workflowShare);

    /**
     * 更新工作流分享
     */
    int update(WorkflowShare workflowShare);

    /**
     * 根据主键删除
     */
    int deleteById(@Param("id") Long id);
}
