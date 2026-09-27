package com.polaris.ai.modelcenter.vo;

import java.util.List;

/** 不暴露旧 Credential/固定参数字段的 Model Aggregate 详情。 */
public record ModelAggregateVO(
        Long id,
        Long tenantId,
        Long deptId,
        String name,
        String modelCode,
        Long connectionId,
        String modelName,
        String modelType,
        String description,
        long revision,
        String status,
        List<ModelCapabilityVO> capabilities,
        List<ModelRuntimePolicyVO> runtimePolicies) {

    public ModelAggregateVO {
        capabilities = capabilities == null ? List.of()
                : List.copyOf(capabilities);
        runtimePolicies = runtimePolicies == null ? List.of()
                : List.copyOf(runtimePolicies);
    }
}
