package com.polaris.ai.workflow.spi;

/** 携带明确租户和环境的资源查询请求。 */
public record WorkflowResourceRequest(
        Long tenantId,
        String environment,
        String resourceKind,
        String resourceKey,
        String resourceId,
        String principalType,
        String principalId,
        Integer resourceVersion) {

    /** 兼容不需要固定资源版本的提供器。 */
    public WorkflowResourceRequest(
            Long tenantId,
            String environment,
            String resourceKind,
            String resourceKey,
            String resourceId,
            String principalType,
            String principalId) {
        this(tenantId, environment, resourceKind, resourceKey, resourceId,
                principalType, principalId, null);
    }
}
