package com.polaris.ai.workflow.spi;

import java.util.Map;

/** 运行时资源句柄；属性中禁止包含凭据。 */
public record ResolvedWorkflowResource(
        String kind,
        String key,
        String resourceId,
        int resourceVersion,
        Map<String, Object> attributes,
        Object handle) {

    public ResolvedWorkflowResource {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
