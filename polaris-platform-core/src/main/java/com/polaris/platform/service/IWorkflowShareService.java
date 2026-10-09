package com.polaris.platform.service;

import com.polaris.ai.workflow.application.WorkflowShareDefaultsCommand;
import com.polaris.ai.workflow.application.WorkflowShareDefinitionView;
import com.polaris.platform.auth.WorkflowShareRateLimiter.RequestType;
import com.polaris.platform.domain.WorkflowShare;

import java.util.List;

/**
 * 工作流分享服务接口
 */
public interface IWorkflowShareService {

    /**
     * 根据租户查询分享列表
     */
    List<WorkflowShare> selectShareListByTenantId(Long tenantId);

    /**
     * 根据主键查询
     */
    WorkflowShare selectShareById(Long id);

    /**
     * 根据分享短码查询
     */
    WorkflowShare selectShareByCode(String shareCode);

    /**
     * 创建工作流分享
     */
    WorkflowShare createShare(WorkflowShare share);

    /**
     * 更新工作流分享
     */
    int updateShare(WorkflowShare share);

    /** 获取当前租户已发布工作流的默认分享配置和推荐类型。 */
    WorkflowShareDefinitionView getShareDefinition(Long workflowDefinitionId);

    WorkflowShareDefinitionView updateShareDefaults(Long workflowDefinitionId, WorkflowShareDefaultsCommand command);

    /**
     * 删除工作流分享
     */
    int deleteShare(Long id);

    /**
     * 根据工作流定义ID查询分享列表
     */
    List<WorkflowShare> selectShareListByWorkflowDefinitionId(Long workflowDefinitionId);

    /**
     * 验证并获取分享详情（状态+过期+读取防刷限流）
     */
    WorkflowShare validateAndGet(String shareCode);

    WorkflowShare validateAndGet(String shareCode, RequestType requestType);
}
