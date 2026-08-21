package com.polaris.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.domain.AiWorkflow;

import java.util.List;

/**
 * AI智能体工作流服务层接口
 *
 * @author polaris
 */
public interface IAiWorkflowService extends IService<AiWorkflow> {

    /**
     * 根据唯一编码查询工作流
     *
     * @param workflowCode 工作流唯一编码
     * @return 工作流编排配置
     */
    AiWorkflow selectWorkflowByCode(String workflowCode);

    /**
     * 查询启用状态的工作流列表
     *
     * @return 工作流列表
     */
    List<AiWorkflow> listActiveWorkflows();

    /**
     * 条件查询工作流列表
     *
     * @param workflow 筛选条件
     * @return 工作流列表
     */
    List<AiWorkflow> selectWorkflowList(AiWorkflow workflow);
}
