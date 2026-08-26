package com.polaris.ai.workflow.spi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;

/** 供编译器和前端动态属性面板使用的节点元数据。 */
public record WorkflowNodeDescriptor(
        String type,
        String handlerVersion,
        String displayName,
        String category,
        JsonNode configSchema,
        JsonNode inputSchema,
        JsonNode outputSchema,
        WorkflowSideEffect sideEffect,
        Set<String> requiredResourceKinds,
        Set<WorkflowNodeCapability> capabilities) {

    public WorkflowNodeDescriptor {
        if (type == null || !type.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("节点类型格式无效: " + type);
        }
        if (handlerVersion == null || !handlerVersion.matches("[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("节点处理器版本格式无效: " + handlerVersion);
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("节点显示名称不能为空");
        }
        category = category == null || category.isBlank() ? "other" : category;
        sideEffect = sideEffect == null ? WorkflowSideEffect.NONE : sideEffect;
        requiredResourceKinds = requiredResourceKinds == null
                ? Set.of() : Set.copyOf(requiredResourceKinds);
        if (requiredResourceKinds.stream()
                .anyMatch(kind -> kind == null || !kind.matches("[A-Z][A-Z0-9_]{0,63}"))) {
            throw new IllegalArgumentException("节点必需资源类型格式无效");
        }
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public boolean supports(WorkflowNodeCapability capability) {
        return capability != null && capabilities.contains(capability);
    }
}
