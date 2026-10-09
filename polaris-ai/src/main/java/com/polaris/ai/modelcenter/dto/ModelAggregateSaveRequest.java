package com.polaris.ai.modelcenter.dto;

import java.util.List;

/** Model Editor 唯一保存协议，不包含任何模型类型专属字段。 */
public record ModelAggregateSaveRequest(
        Long id,
        Long expectedRevision,
        String name,
        String modelCode,
        Long connectionId,
        String modelName,
        String modelType,
        String description,
        Long deptId,
        String status,
        List<ModelCapabilitySaveRequest> capabilities,
        List<ModelRuntimePolicySaveRequest> runtimePolicies) {

    public ModelAggregateSaveRequest {
        capabilities = capabilities == null ? List.of()
                : List.copyOf(capabilities);
        runtimePolicies = runtimePolicies == null ? List.of()
                : List.copyOf(runtimePolicies);
    }
}
