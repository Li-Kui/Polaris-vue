package com.polaris.ai.modelcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.polaris.ai.domain.AiModelRuntimePolicy;
import com.polaris.ai.mapper.AiModelRuntimePolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Model Aggregate 内部使用的运行策略查询服务。 */
@Service
@Transactional(readOnly = true)
public class ModelRuntimePolicyInternalService {

    private final AiModelRuntimePolicyMapper runtimePolicyMapper;
    private final ModelAggregateAccessGuard accessGuard;

    public ModelRuntimePolicyInternalService(
            AiModelRuntimePolicyMapper runtimePolicyMapper,
            ModelAggregateAccessGuard accessGuard) {
        this.runtimePolicyMapper = runtimePolicyMapper;
        this.accessGuard = accessGuard;
    }

    public List<AiModelRuntimePolicy> listByModelId(Long modelConfigId) {
        accessGuard.requireAccessible(modelConfigId);
        return runtimePolicyMapper.selectList(new LambdaQueryWrapper<AiModelRuntimePolicy>()
                .eq(AiModelRuntimePolicy::getModelConfigId,
                        Objects.requireNonNull(modelConfigId, "modelConfigId"))
                .orderByAsc(AiModelRuntimePolicy::getCapabilityCode));
    }

    public AiModelRuntimePolicy findByModelAndCapability(
            Long modelConfigId, String capabilityCode) {
        accessGuard.requireAccessible(modelConfigId);
        return runtimePolicyMapper.selectOne(new LambdaQueryWrapper<AiModelRuntimePolicy>()
                .eq(AiModelRuntimePolicy::getModelConfigId,
                        Objects.requireNonNull(modelConfigId, "modelConfigId"))
                .eq(AiModelRuntimePolicy::getCapabilityCode,
                        normalizeCapabilityCode(capabilityCode)));
    }

    private String normalizeCapabilityCode(String capabilityCode) {
        return capabilityCode == null ? "" : capabilityCode;
    }
}
