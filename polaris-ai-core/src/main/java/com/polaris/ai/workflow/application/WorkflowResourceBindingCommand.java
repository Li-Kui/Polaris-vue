package com.polaris.ai.workflow.application;

/** 创建或更新当前所有者环境逻辑资源绑定的命令。 */
public record WorkflowResourceBindingCommand(
        Long id,
        Long definitionId,
        String scopeType,
        String environment,
        String resourceKind,
        String resourceKey,
        String resourceId,
        Integer expectedLockVersion) {
}
