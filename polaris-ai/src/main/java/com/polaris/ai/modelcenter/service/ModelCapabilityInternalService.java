package com.polaris.ai.modelcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.AiModelCapability;
import com.polaris.ai.mapper.AiModelCapabilityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Model Aggregate 内部使用的能力查询服务。 */
@Service
@Transactional(readOnly = true)
public class ModelCapabilityInternalService {

    private final AiModelCapabilityMapper capabilityMapper;
    private final ModelAggregateAccessGuard accessGuard;

    public ModelCapabilityInternalService(
            AiModelCapabilityMapper capabilityMapper,
            ModelAggregateAccessGuard accessGuard) {
        this.capabilityMapper = capabilityMapper;
        this.accessGuard = accessGuard;
    }

    public List<AiModelCapability> listByModelId(Long modelConfigId) {
        return capabilityMapper.selectList(baseQuery(modelConfigId)
                .orderByAsc(AiModelCapability::getCapabilityCode)
                .orderByAsc(AiModelCapability::getAppliesToCapabilityCode));
    }

    public List<AiModelCapability> listEnabledByModelId(Long modelConfigId) {
        return capabilityMapper.selectList(baseQuery(modelConfigId)
                .eq(AiModelCapability::getEnabled, "1")
                .orderByAsc(AiModelCapability::getCapabilityCode)
                .orderByAsc(AiModelCapability::getAppliesToCapabilityCode));
    }

    private LambdaQueryWrapper<AiModelCapability> baseQuery(Long modelConfigId) {
        accessGuard.requireAccessible(modelConfigId);
        return new LambdaQueryWrapper<AiModelCapability>()
                .eq(AiModelCapability::getModelConfigId,
                        Objects.requireNonNull(modelConfigId, "modelConfigId"));
    }
}
