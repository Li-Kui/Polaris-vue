package com.polaris.ai.workflow.application;

import java.util.Map;

/** 可在工作流节点中选择的现有资源安全摘要。 */
public record WorkflowResourceOption(
        String kind,
        String resourceId,
        String name,
        String description,
        String status,
        boolean available,
        String unavailableReason,
        boolean shared,
        Map<String, Object> attributes) {
}
