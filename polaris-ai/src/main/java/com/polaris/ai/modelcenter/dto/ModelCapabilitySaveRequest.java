package com.polaris.ai.modelcenter.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** Schema 驱动的 Capability 持久化请求。 */
public record ModelCapabilitySaveRequest(
        String capabilityCode,
        String appliesToCapabilityCode,
        Integer schemaVersion,
        Boolean enabled,
        Map<String, Object> config) {

    public ModelCapabilitySaveRequest {
        if (config == null || config.isEmpty()) {
            config = Map.of();
        } else {
            Map<String, Object> compact = new LinkedHashMap<>();
            config.forEach((key, value) -> {
                if (key != null && value != null) {
                    compact.put(key, value);
                }
            });
            config = Map.copyOf(compact);
        }
    }
}
