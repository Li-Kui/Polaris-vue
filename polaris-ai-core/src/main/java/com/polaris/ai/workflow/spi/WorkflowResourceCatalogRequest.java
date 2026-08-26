package com.polaris.ai.workflow.spi;

/** 查询当前主体可用于工作流的现有资源。 */
public record WorkflowResourceCatalogRequest(
        Long tenantId,
        String environment,
        String principalType,
        String principalId) {
}
