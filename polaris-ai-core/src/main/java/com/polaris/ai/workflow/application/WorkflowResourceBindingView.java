package com.polaris.ai.workflow.application;

import java.util.Date;

/** 对调用方安全的逻辑资源绑定视图。 */
public record WorkflowResourceBindingView(
        Long id,
        String ownerType,
        Long ownerId,
        Long tenantId,
        String environment,
        String resourceKind,
        String resourceKey,
        String resourceId,
        Integer bindingVersion,
        String status,
        Integer lockVersion,
        Date createTime,
        Date updateTime) {
}
