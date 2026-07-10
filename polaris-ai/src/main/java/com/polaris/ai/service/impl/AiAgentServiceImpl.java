package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.domain.AiAgent;
import com.polaris.ai.mapper.AiAgentMapper;
import com.polaris.ai.service.IAiAgentService;
import com.polaris.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI智能体配置服务层实现类
 *
 * @author polaris
 */
@Slf4j
@Service
public class AiAgentServiceImpl extends ServiceImpl<AiAgentMapper, AiAgent> implements IAiAgentService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.polaris.ai.pivot.AiModelFactory modelFactory;

    private void fillModelName(AiAgent entity) {
        if (entity != null && entity.getModelConfigId() != null) {
            com.polaris.ai.domain.AiModelConfig config = modelFactory.getModelConfig(entity.getModelConfigId());
            if (config != null) {
                entity.setModelName(config.getModelName());
            }
        }
    }

    @Override
    public boolean save(AiAgent entity) {
        fillModelName(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(AiAgent entity) {
        fillModelName(entity);
        return super.updateById(entity);
    }

    @Override
    public AiAgent selectAgentByCode(String agentCode) {
        if (StringUtils.isEmpty(agentCode)) {
            return null;
        }
        return lambdaQuery()
                .eq(AiAgent::getAgentCode, agentCode)
                .eq(AiAgent::getStatus, "1") // 只查启用的
                .one();
    }

    @Override
    public List<AiAgent> selectAgentList(AiAgent agent) {
        LambdaQueryWrapper<AiAgent> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiAgent::getDelFlag, "0"); // 仅未删除的
        if (agent != null) {
            if (StringUtils.isNotEmpty(agent.getAgentCode())) {
                queryWrapper.eq(AiAgent::getAgentCode, agent.getAgentCode());
            }
            if (StringUtils.isNotEmpty(agent.getAgentName())) {
                queryWrapper.like(AiAgent::getAgentName, agent.getAgentName());
            }
            if (StringUtils.isNotEmpty(agent.getStatus())) {
                queryWrapper.eq(AiAgent::getStatus, agent.getStatus());
            }
        }
        queryWrapper.orderByDesc(AiAgent::getId);
        return list(queryWrapper);
    }
}
