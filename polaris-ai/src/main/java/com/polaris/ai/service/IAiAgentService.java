package com.polaris.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.polaris.ai.domain.AiAgent;

import java.util.List;

/**
 * AI智能体配置服务层接口
 *
 * @author polaris
 */
public interface IAiAgentService extends IService<AiAgent> {

    /**
     * 根据智能体唯一编码查询智能体
     *
     * @param agentCode 智能体唯一编码
     * @return 智能体配置
     */
    AiAgent selectAgentByCode(String agentCode);

    /**
     * 查询智能体列表
     *
     * @param agent 筛选条件
     * @return 智能体列表
     */
    List<AiAgent> selectAgentList(AiAgent agent);
}
