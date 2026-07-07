package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.mapper.AiWorkflowMapper;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI智能体工作流服务层实现类
 *
 * @author polaris
 */
@Slf4j
@Service
public class AiWorkflowServiceImpl extends ServiceImpl<AiWorkflowMapper, AiWorkflow> implements IAiWorkflowService {

    @Override
    public AiWorkflow selectWorkflowByCode(String workflowCode) {
        if (StringUtils.isEmpty(workflowCode)) {
            return null;
        }
        return lambdaQuery()
                .eq(AiWorkflow::getWorkflowCode, workflowCode)
                .eq(AiWorkflow::getStatus, "1") // 仅查启用的
                .one();
    }

    @Override
    public List<AiWorkflow> listActiveWorkflows() {
        return lambdaQuery()
                .eq(AiWorkflow::getStatus, "1")
                .eq(AiWorkflow::getDelFlag, "0")
                .orderByDesc(AiWorkflow::getId)
                .list();
    }

    @Override
    public List<AiWorkflow> selectWorkflowList(AiWorkflow workflow) {
        LambdaQueryWrapper<AiWorkflow> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiWorkflow::getDelFlag, "0");
        if (workflow != null) {
            if (StringUtils.isNotEmpty(workflow.getWorkflowCode())) {
                queryWrapper.eq(AiWorkflow::getWorkflowCode, workflow.getWorkflowCode());
            }
            if (StringUtils.isNotEmpty(workflow.getWorkflowName())) {
                queryWrapper.like(AiWorkflow::getWorkflowName, workflow.getWorkflowName());
            }
            if (StringUtils.isNotEmpty(workflow.getStatus())) {
                queryWrapper.eq(AiWorkflow::getStatus, workflow.getStatus());
            }
        }
        queryWrapper.orderByDesc(AiWorkflow::getId);
        return list(queryWrapper);
    }
}
