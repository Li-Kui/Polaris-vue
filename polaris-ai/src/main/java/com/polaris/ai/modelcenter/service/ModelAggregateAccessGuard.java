package com.polaris.ai.modelcenter.service;

import com.polaris.ai.domain.AiModelConfig;
import com.polaris.ai.mapper.AiModelConfigMapper;
import com.polaris.common.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 通过模型主表的现有租户拦截链验证 Model Aggregate 可访问性。 */
@Component
public class ModelAggregateAccessGuard {

    private final AiModelConfigMapper modelConfigMapper;

    public ModelAggregateAccessGuard(AiModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    public AiModelConfig requireAccessible(Long modelConfigId) {
        Long id = Objects.requireNonNull(modelConfigId, "modelConfigId");
        AiModelConfig model = modelConfigMapper.selectById(id);
        if (model == null) {
            throw new ServiceException("模型配置不存在或无权访问");
        }
        return model;
    }
}
