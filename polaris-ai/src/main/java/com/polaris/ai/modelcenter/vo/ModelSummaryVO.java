package com.polaris.ai.modelcenter.vo;

import java.util.List;

/** Model Center 列表仅返回稳定字段与 Capability，不回显 Credential/旧参数。 */
public record ModelSummaryVO(
        Long id,
        String name,
        String modelCode,
        Long connectionId,
        String connectionName,
        String modelName,
        String modelType,
        String description,
        long revision,
        String status,
        List<String> enabledCapabilities) {

    public ModelSummaryVO {
        enabledCapabilities = enabledCapabilities == null
                ? List.of() : List.copyOf(enabledCapabilities);
    }
}
