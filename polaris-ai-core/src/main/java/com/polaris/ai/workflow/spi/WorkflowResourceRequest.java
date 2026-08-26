package com.polaris.ai.workflow.spi;

/** 携带明确租户和环境的资源查询请求。 */
public record WorkflowResourceRequest(
        Long tenantId,
        String environment,
        String resourceKind,
        String resourceKey,
        String resourceId,
        String principalType,
        String principalId) {
}
