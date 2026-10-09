package com.polaris.ai.modelcenter.vo;

import com.polaris.ai.modelcenter.schema.CapabilitySchemaDefinition;

import java.util.Map;

/** Model Editor 的单项 Capability 状态与可信 Resolved Schema。 */
public record ModelCapabilityVO(
        String key,
        String capabilityCode,
        String appliesToCapabilityCode,
        int schemaVersion,
        String schemaHash,
        boolean enabled,
        String capabilitySource,
        Map<String, Object> config,
        CapabilitySchemaDefinition schema) {

    public ModelCapabilityVO {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
