package com.polaris.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaris.ai.domain.AiWorkflow;
import com.polaris.ai.mapper.AiWorkflowMapper;
import com.polaris.ai.service.IAiWorkflowService;
import com.polaris.ai.workflow.langgraph.GraphTopology;
import com.polaris.common.exception.ServiceException;
import com.polaris.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI智能体工作流服务层实现类
 *
 * @author polaris
 */
@Service
public class AiWorkflowServiceImpl extends ServiceImpl<AiWorkflowMapper, AiWorkflow> implements IAiWorkflowService {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean save(AiWorkflow entity) {
        validateGraphJson(entity == null ? null : entity.getGraphJson(), true);
        entity.setVersion(1);
        return super.save(entity);
    }

    @Override
    public boolean updateById(AiWorkflow entity) {
        if (entity == null || entity.getId() == null) {
            throw new ServiceException("工作流 ID 不能为空");
        }
        if (StringUtils.isNotEmpty(entity.getGraphJson())) {
            validateGraphJson(entity.getGraphJson(), true);
        }
        if (entity.getVersion() == null) {
            AiWorkflow current = getById(entity.getId());
            if (current == null) {
                throw new ServiceException("工作流不存在");
            }
            entity.setVersion(current.getVersion() == null ? 1 : current.getVersion());
        }
        boolean updated = super.updateById(entity);
        if (!updated) {
            throw new ServiceException("工作流已被其他用户修改，请刷新后重试");
        }
        return true;
    }

    private void validateGraphJson(String graphJson, boolean required) {
        if (StringUtils.isEmpty(graphJson)) {
            if (required) {
                throw new ServiceException("工作流图配置不能为空");
            }
            return;
        }
        try {
            GraphTopology topology = objectMapper.readValue(graphJson, GraphTopology.class);
            topology.validate();
        } catch (IllegalArgumentException e) {
            throw new ServiceException(e.getMessage());
        } catch (Exception e) {
            throw new ServiceException("工作流图配置无法解析");
        }
    }

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
